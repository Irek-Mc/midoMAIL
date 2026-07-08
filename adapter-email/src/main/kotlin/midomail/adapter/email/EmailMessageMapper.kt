package midomail.adapter.email

import jakarta.activation.DataHandler
import jakarta.mail.Message.RecipientType
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import midomail.domain.message.Attachment
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CausationId
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.AttachmentStore
import midomail.domain.port.MessageStore
import java.time.Instant
import java.util.UUID

/**
 * Mapowanie GatewayMessage ↔ MIME (20-Adapters/22-Adapter-Email.md, §6, §7).
 *
 * Temat wiadomości nie jest częścią kanonicznego modelu GatewayMessage (SMS/webhook nie mają
 * tematu) — przenoszony przez `Attributes["subject"]` (SPEC-0001-GatewayMessage.md, §Attributes:
 * „generyczny, rozszerzalny bagaż... bez z góry ustalonej struktury").
 *
 * [fromDisplayName] — nazwa wyświetlana nadawcy w kliencie pocztowym (np. „midoMAIL SMS/MMS" na
 * Androidzie), zamiast samego surowego adresu (np. `gateway@example.com`) — zgłoszone przez
 * nadzorcę projektu jako mylące. Opcjonalny, z generyczną wartością domyślną — `:platform-jvm`
 * (gdzie kontekst SMS/MMS nie występuje) może zostawić wartość domyślną lub podać własną.
 *
 * Brak `Attributes["subject"]` (typowe dla wiadomości pochodzących z SMS/MMS, które nie mają
 * pojęcia tematu) skutkuje tematem zastępczym z adresem/numerem nadawcy — zgłoszone przez
 * nadzorcę projektu: pusty temat w skrzynce odbiorczej utrudniał rozpoznanie, kto pisze.
 */
class EmailMessageMapper(
    private val attachmentStore: AttachmentStore,
    private val fromDisplayName: String = "midoMAIL Gateway"
) {

    /**
     * [messageStore] (opcjonalny, `null` domyślnie nie ustawia nagłówków wątkowania) — gdy
     * podany, i ta wiadomość niesie korelację odziedziczoną z wcześniejszej rozmowy (np. kolejny
     * SMS od tego samego numeru po korelacji w [midomail.adapter.gsm.SmsMessageMapper]), nagłówki
     * `In-Reply-To`/`References` wskazują na ostatni e-mail w tej korelacji, który NAPRAWDĘ
     * przyszedł przez e-mail (ma prawdziwy, nadany przez klienta pocztowy `Message-ID` jako swój
     * `ExternalReference`, w przeciwieństwie do SMS-a, którego `ExternalReference` to skrót
     * SHA-256) — dzięki temu Gmail grupuje CAŁĄ rozmowę SMS↔e-mail w jeden ciągły wątek zamiast
     * traktować każdą kolejną odpowiedź nadawcy jako nowy wątek
     * (ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md).
     */
    fun toMime(message: GatewayMessage, session: Session, messageStore: MessageStore? = null): MimeMessage {
        val mimeMessage = MimeMessage(session)
        val sourceAddress = requireAddress(message.source.address, "source")
        mimeMessage.setFrom(InternetAddress(sourceAddress, fromDisplayName))
        mimeMessage.setRecipient(RecipientType.TO, InternetAddress(requireAddress(message.destination.address, "destination")))
        mimeMessage.subject = message.attributes["subject"] ?: sourceAddress

        findLastEmailReferenceInThread(message, messageStore)?.let { reference ->
            mimeMessage.setHeader("In-Reply-To", reference)
            mimeMessage.setHeader("References", reference)
        }

        if (message.payload.attachments.isEmpty()) {
            mimeMessage.setText(message.payload.content, "UTF-8")
        } else {
            val multipart = MimeMultipart()

            val textPart = MimeBodyPart()
            textPart.setText(message.payload.content, "UTF-8")
            multipart.addBodyPart(textPart)

            message.payload.attachments.forEach { attachment ->
                val attachmentPart = MimeBodyPart()
                val bytes = attachmentStore.read(attachment.dataReference)
                attachmentPart.dataHandler = DataHandler(ByteArrayDataSource(bytes, attachment.contentType))
                // MimeBodyPart nie synchronizuje nagłówka Content-Type z DataHandler automatycznie
                // (getContentType() domyślnie zwraca "text/plain" bez jawnego nagłówka) — ustawiane wprost.
                attachmentPart.setHeader("Content-Type", attachment.contentType)
                attachmentPart.fileName = attachment.fileName
                multipart.addBodyPart(attachmentPart)
            }

            mimeMessage.setContent(multipart)
        }

        return mimeMessage
    }

    /**
     * [messageStore] jest wykorzystywany wyłącznie do odczytu, zgodnie z SPEC-0010-Plugin-SDK-Contract.md,
     * §Porty przekazywane adapterowi — do ustalenia CorrelationId/CausationId nici na podstawie
     * `In-Reply-To`/`References` (22-Adapter-Email.md, §6).
     *
     * `ExternalReference` = nagłówek `Message-ID` (RFC 5322). Wiadomość bez tego nagłówka jest
     * odrzucana (błąd), a nie tworzona z wymyśloną wartością — wymyślona wartość unieważniłaby
     * Exactly Once (ta sama wiadomość wyglądałaby jako nowa przy każdym odczycie).
     */
    fun fromMime(mimeMessage: MimeMessage, sourceType: ChannelType, messageStore: MessageStore): GatewayMessage {
        val externalReference = extractMessageId(mimeMessage)
        val parent = findParent(mimeMessage, messageStore)
        val (correlationId, causationId) = threadingFrom(parent)

        val fromAddress = (mimeMessage.from?.firstOrNull() as? InternetAddress)?.address
        val toAddress = (mimeMessage.getRecipients(RecipientType.TO)?.firstOrNull() as? InternetAddress)?.address

        val (content, attachments) = extractContent(mimeMessage)

        return GatewayMessage(
            identity = Identity(
                messageId = MessageId(UUID.randomUUID().toString()),
                correlationId = correlationId,
                causationId = causationId,
                schemaVersion = SchemaVersion("2.0"),
                externalReference = externalReference
            ),
            source = Channel(type = sourceType, address = fromAddress),
            destination = resolveDestination(parent, sourceType, toAddress),
            payload = Payload(content = stripQuotedReply(content), attachments = attachments),
            attributes = mimeMessage.subject?.let { mapOf("subject" to it) } ?: emptyMap()
        )
    }

    private fun findLastEmailReferenceInThread(message: GatewayMessage, messageStore: MessageStore?): String? {
        val store = messageStore ?: return null
        return store.findByCorrelationId(message.identity.correlationId)
            .filter { it.source.type == ChannelType("email") }
            .maxByOrNull { store.metadataFor(it.identity.messageId)?.createdAt ?: Instant.MIN }
            ?.identity?.externalReference?.value
    }

    private fun requireAddress(address: String?, role: String): String =
        requireNotNull(address) { "Channel.address ($role) jest wymagany do wysyłki e-mail, otrzymano null" }

    private fun extractMessageId(mimeMessage: MimeMessage): ExternalReference {
        val messageId = mimeMessage.getHeader("Message-ID")?.firstOrNull()
        checkNotNull(messageId) { "Wiadomość bez nagłówka Message-ID nie może być bezpiecznie deduplikowana (Exactly Once)" }
        return ExternalReference(messageId)
    }

    private fun findParent(mimeMessage: MimeMessage, messageStore: MessageStore): GatewayMessage? {
        val candidates = buildList {
            mimeMessage.getHeader("In-Reply-To")?.firstOrNull()?.let { add(it) }
            mimeMessage.getHeader("References")?.firstOrNull()?.split(Regex("\\s+"))?.asReversed()?.forEach { add(it) }
        }

        for (candidate in candidates) {
            messageStore.findByExternalReference(ExternalReference(candidate))?.let { return it }
        }
        return null
    }

    private fun threadingFrom(parent: GatewayMessage?): Pair<CorrelationId, CausationId?> {
        if (parent != null) {
            return parent.identity.correlationId to CausationId(parent.identity.messageId.value)
        }
        // Brak wątku nadrzędnego w Message Store — ta wiadomość jest korzeniem nowej nici,
        // koreluje samą siebie (nowy MessageId nie jest jeszcze znany tutaj, więc korelacja
        // odbywa się przez świeżo wygenerowany identyfikator nici).
        return CorrelationId(UUID.randomUUID().toString()) to null
    }

    /**
     * Jeśli ta wiadomość jest odpowiedzią na coś, co PIERWOTNIE przyszło z innego kanału niż
     * e-mail (np. SMS przekazany dalej jako e-mail, Iteracja 3.13 — pełny łańcuch
     * SMS → Email → odpowiedź → SMS) — odpowiedź powinna wrócić do TEGO kanału/adresu (numeru
     * telefonu oryginału), nie do adresu e-mail, spod którego nadeszła ta konkretna odpowiedź.
     * W przeciwnym razie (wątek czysto e-mailowy, albo brak wątku) zachowanie niezmienione —
     * adres z nagłówka `To:` tej wiadomości.
     */
    private fun resolveDestination(parent: GatewayMessage?, sourceType: ChannelType, toAddress: String?): Channel {
        if (parent != null && parent.source.type != sourceType) {
            return parent.source
        }
        return Channel(type = sourceType, address = toAddress)
    }

    /**
     * Klienci pocztowi (Gmail potwierdzone na urządzeniu) automatycznie doklejają pod odpowiedzią
     * cały cytowany wątek („<data>\n<nadawca> napisał(a):\n\n> <oryginał>") — bez obcięcia,
     * przekazana dalej jako SMS wiadomość niosłaby CAŁĄ historię korespondencji zamiast wyłącznie
     * nowej treści odpowiedzi (zgłoszone przez nadzorcę projektu: „nadawca ma nawet nie wiedzieć,
     * że wiadomość nie pochodzi z telefonu").
     *
     * Wykrywa pierwszą linię pasującą do nagłówka cytowania (angielskie „wrote:"/polskie
     * „napisał(a):" — Gmail w obu językach), cofa się przez bezpośrednio poprzedzające,
     * niepuste linie (typowy dodatkowy wiersz z datą w polskiej wersji Gmaila) i obcina wszystko
     * od tego miejsca. Świadome ograniczenie: rozpoznaje wyłącznie te dwa warianty nagłówka
     * (Gmail) — inne klienty pocztowe (Outlook, Apple Mail) mogą używać innego formatu cytowania,
     * nierozpoznawanego przez ten wzorzec; brak dopasowania oznacza treść bez zmian, nie błąd.
     */
    private fun stripQuotedReply(text: String): String {
        val lines = text.lines()
        var cutIndex = lines.indexOfFirst { QUOTE_HEADER_PATTERN.matches(it.trim()) }
        if (cutIndex == -1) return text.trim()

        while (cutIndex > 0 && lines[cutIndex - 1].isNotBlank()) {
            cutIndex--
        }

        return lines.subList(0, cutIndex).joinToString("\n").trim()
    }

    private fun extractContent(mimeMessage: MimeMessage): Pair<String, List<Attachment>> {
        val content = mimeMessage.content
        if (content !is Multipart) {
            return content.toString() to emptyList()
        }

        var text = ""
        val attachments = mutableListOf<Attachment>()
        for (index in 0 until content.count) {
            val part = content.getBodyPart(index)
            if (part.disposition == Part.ATTACHMENT || part.fileName != null) {
                val bytes = part.inputStream.readBytes()
                val reference = attachmentStore.write(bytes)
                attachments.add(
                    Attachment(
                        contentType = part.contentType,
                        fileName = part.fileName ?: "attachment",
                        size = bytes.size.toLong(),
                        dataReference = reference
                    )
                )
            } else if (part.isMimeType("text/plain") && text.isEmpty()) {
                text = part.content.toString()
            }
        }
        return text to attachments
    }

    private companion object {
        val QUOTE_HEADER_PATTERN = Regex(".*(napisał\\(a\\)|wrote):\\s*", RegexOption.IGNORE_CASE)
    }
}
