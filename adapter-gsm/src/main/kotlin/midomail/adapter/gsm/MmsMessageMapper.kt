package midomail.adapter.gsm

import midomail.domain.message.Attachment
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.AttachmentStore
import java.security.MessageDigest
import java.util.UUID

/** Jedna część MMS wyodrębniona z pobranego PDU/MMS Provider — proste dane, nie obiekt Android. */
data class MmsPart(val contentType: String, val fileName: String?, val bytes: ByteArray)

/** Przychodząca wiadomość MMS po pobraniu (Iteracja 3.10) — wejście do mapowania. */
data class MmsMessage(val sender: String, val timestampMillis: Long, val parts: List<MmsPart>)

/**
 * Mapowanie MMS → GatewayMessage (20-Adapters/21-Adapter-GSM.md, §7). MMS jest pełnoprawnym,
 * odrębnym transportem obok SMS — nie jest traktowany jako rozszerzenie SMS (ADR-0003).
 *
 * Część `text/plain` staje się treścią główną (konkatenacja przy wielu częściach tekstowych);
 * `application/smil` (opis układu prezentacji slajdów MMS) jest pomijana — nie niesie treści
 * użytkowej; wszystkie pozostałe części (obrazy, audio, wideo itd.) trafiają jako załączniki przez
 * [AttachmentStore] (port z Fazy 2, ADR-0013/SPEC-0016 — ponownie użyty, nie duplikowany).
 *
 * `ExternalReference` — ten sam schemat co SMS (Iteracja 3.5): SHA-256 z nadawcy, znacznika czasu
 * i treści tekstowej (nie z bajtów załączników — kosztowne i niepotrzebne dla deduplikacji).
 * Bez wątkowania, z tych samych powodów co SMS (brak nagłówków `In-Reply-To`/`References`).
 *
 * [forwardToAddress] — patrz `SmsMessageMapper`, ten sam mechanizm (Iteracja 3.13).
 */
class MmsMessageMapper(private val attachmentStore: AttachmentStore, private val forwardToAddress: String? = null) {

    fun fromMms(mms: MmsMessage): GatewayMessage {
        val textContent = mms.parts
            .filter { it.contentType.startsWith("text/plain") }
            .joinToString(separator = "") { String(it.bytes, Charsets.UTF_8) }

        val attachments = mms.parts
            .filterNot { it.contentType.startsWith("text/plain") || it.contentType.startsWith("application/smil") }
            .map { part ->
                Attachment(
                    contentType = part.contentType,
                    fileName = part.fileName ?: "attachment",
                    size = part.bytes.size.toLong(),
                    dataReference = attachmentStore.write(part.bytes)
                )
            }

        return GatewayMessage(
            identity = Identity(
                messageId = MessageId(UUID.randomUUID().toString()),
                correlationId = CorrelationId(UUID.randomUUID().toString()),
                causationId = null,
                schemaVersion = SchemaVersion("2.0"),
                externalReference = computeExternalReference(mms.sender, textContent, mms.timestampMillis)
            ),
            source = Channel(type = ChannelType("mms"), address = mms.sender),
            destination = Channel(type = ChannelType("mms"), address = forwardToAddress),
            payload = Payload(content = textContent, attachments = attachments)
        )
    }

    private fun computeExternalReference(sender: String, textContent: String, timestampMillis: Long): ExternalReference {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sender|$timestampMillis|$textContent".toByteArray(Charsets.UTF_8))
        return ExternalReference(digest.joinToString("") { "%02x".format(it) })
    }
}
