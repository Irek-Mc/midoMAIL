package midomail.domain.notification

import midomail.domain.adapter.Adapter
import midomail.domain.health.Alert
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import java.util.UUID

/**
 * Kanał powiadomień e-mail (SPEC-0019-Notification-Channel-Contract.md) — wykorzystuje już
 * zarejestrowaną, żywą instancję `EmailAdapter` przekazaną z zewnątrz (punkt kompozycji), wołając
 * [Adapter.send] BEZPOŚREDNIO. Celowo pomija `GatewayEngine`/`RoutingEngine`/`ExactlyOnceEngine` —
 * powiadomienie nie jest wiadomością biznesową podlegającą routingowi/deduplikacji Gateway
 * (ADR-0016-Notification-Channel-Port.md; 38-Powiadomienia.md §2: „nie wpływa na przetwarzanie
 * komunikatów").
 *
 * [emailAdapter] jest typu [Adapter] (nie konkretnie `EmailAdapter` z `:adapter-email`) — `:domain`
 * nie może zależeć od `:adapter-email` (kierunek zależności odwrotny do reszty projektu); dowolny
 * zarejestrowany adapter obsługujący kanał e-mail spełnia ten kontrakt.
 */
class EmailNotificationChannel(
    private val emailAdapter: Adapter,
    private val fromAddress: String,
    private val toAddress: String
) : NotificationChannel {

    override fun deliver(alert: Alert): NotificationResult = try {
        emailAdapter.send(toMessage(alert))
        NotificationResult.Delivered
    } catch (exception: Exception) {
        NotificationResult.Failed(exception.message ?: "Nieznany błąd wysyłki powiadomienia e-mail")
    }

    private fun toMessage(alert: Alert): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId(UUID.randomUUID().toString()),
            correlationId = alert.correlationId ?: CorrelationId(UUID.randomUUID().toString()),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference("alert-${alert.alertId.value}")
        ),
        source = Channel(type = ChannelType("email"), address = fromAddress),
        destination = Channel(type = ChannelType("email"), address = toAddress),
        payload = Payload(content = body(alert)),
        attributes = mapOf("subject" to "[midoMAIL ${alert.level}] ${alert.source.value}")
    )

    private fun body(alert: Alert): String = buildString {
        appendLine("Poziom: ${alert.level}")
        appendLine("Źródło: ${alert.source.value}")
        appendLine("Czas: ${alert.timestamp}")
        appendLine("Status: ${alert.status}")
        alert.recommendedAction?.let { appendLine("Zalecane działanie: $it") }
    }
}
