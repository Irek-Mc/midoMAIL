package midomail.adapter.gsm

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
import midomail.domain.port.MessageQueryFilter
import midomail.domain.port.MessageSort
import midomail.domain.port.MessageSortField
import midomail.domain.port.MessageStore
import midomail.domain.port.PageRequest
import midomail.domain.port.SortDirection
import java.security.MessageDigest
import java.util.UUID

/**
 * Mapowanie SMS → GatewayMessage (20-Adapters/21-Adapter-GSM.md, §6).
 *
 * Celowo operuje na prostych typach (nie na `android.telephony.SmsMessage`) — ekstrakcja z PDU
 * jest cienką warstwą w [SmsDeliverReceiver] (Iteracja 3.8); ta klasa jest czystym Kotlinem,
 * testowalnym bez urządzenia (50-Quality/50-Testy.md, §5).
 *
 * SMS nie ma naturalnego odpowiednika nagłówka `Message-ID` z RFC 5322 (w przeciwieństwie do
 * e-maila, 22-Adapter-Email.md, §6) — [ExternalReference] jest wyznaczany jako SHA-256 z
 * (nadawca, znacznik czasu, treść), deterministyczny i liczony wyłącznie z danych dostępnych
 * natychmiast przy odbiorze, bez zależności od zapisu do systemowego SMS Provider.
 *
 * [messageStore] (opcjonalny, `null` domyślnie zachowuje poprzednie zachowanie) — SMS nie niesie
 * nagłówków `In-Reply-To`/`References` jak e-mail, więc wątkowanie po treści protokołu jest
 * niemożliwe; zamiast tego, jeśli podany, każda przychodząca wiadomość szuka NAJNOWSZEJ istniejącej
 * wiadomości z tym samym numerem nadawcy (traktując numer telefonu jako trwały identyfikator
 * rozmowy — tak jak natywna aplikacja SMS) i dziedziczy jej CorrelationId, zamiast zaczynać nową
 * nić (ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md — zgłoszenie nadzorcy projektu: odpowiedź
 * na przekazanego SMS-a wracała jako osobny wątek e-mail zamiast kontynuacji tej samej rozmowy).
 */
class SmsMessageMapper(
    private val forwardToAddress: String? = null,
    private val messageStore: MessageStore? = null
) {

    fun fromSms(sender: String, body: String, timestampMillis: Long): GatewayMessage {
        val (correlationId, causationId) = resolveThreading(sender)
        return GatewayMessage(
            identity = Identity(
                messageId = MessageId(UUID.randomUUID().toString()),
                correlationId = correlationId,
                causationId = causationId,
                schemaVersion = SchemaVersion("2.0"),
                externalReference = computeExternalReference(sender, body, timestampMillis)
            ),
            source = Channel(type = ChannelType("sms"), address = sender),
            destination = Channel(type = ChannelType("sms"), address = forwardToAddress),
            payload = Payload(content = body)
        )
    }

    private fun resolveThreading(sender: String): Pair<CorrelationId, CausationId?> {
        val store = messageStore ?: return CorrelationId(UUID.randomUUID().toString()) to null

        val latest = store.query(
            filter = MessageQueryFilter(channelAddress = sender),
            sort = MessageSort(field = MessageSortField.CREATED_AT, direction = SortDirection.DESCENDING),
            page = PageRequest(size = 1)
        ).items.firstOrNull()

        return if (latest != null) {
            latest.identity.correlationId to CausationId(latest.identity.messageId.value)
        } else {
            CorrelationId(UUID.randomUUID().toString()) to null
        }
    }

    private fun computeExternalReference(sender: String, body: String, timestampMillis: Long): ExternalReference {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sender|$timestampMillis|$body".toByteArray(Charsets.UTF_8))
        return ExternalReference(digest.joinToString("") { "%02x".format(it) })
    }
}
