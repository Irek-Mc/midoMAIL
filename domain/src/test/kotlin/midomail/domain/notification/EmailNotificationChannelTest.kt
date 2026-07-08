package midomail.domain.notification

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Testy jednostkowe na atrapie [Adapter] (bez jakarta.mail) — pełny przebieg z rzeczywistym
 * `EmailAdapter`/GreenMail żyje w `:adapter-email` (`:domain` nie może zależeć od `:adapter-email`).
 */
class EmailNotificationChannelTest {

    private class RecordingAdapter(var onSend: (GatewayMessage) -> Unit = {}) : Adapter {
        override val adapterId = AdapterId("email-primary")
        override val adapterVersion = "1.0"
        var lastSent: GatewayMessage? = null

        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 0, 0, 0)
        override fun send(message: GatewayMessage) {
            lastSent = message
            onSend(message)
        }
    }

    private fun createAlert(): Alert = Alert(
        alertId = AlertId("alert-1"),
        level = AlertLevel.CRITICAL,
        source = SourceComponent("gsm-primary"),
        timestamp = Instant.parse("2026-07-06T12:00:00Z"),
        status = AlertStatus.ACTIVE,
        recommendedAction = "Sprawdź kartę SIM"
    )

    @Test
    fun `deliver sends an email built from the Alert and returns Delivered`() {
        val adapter = RecordingAdapter()
        val channel = EmailNotificationChannel(adapter, fromAddress = "gateway@example.com", toAddress = "ops@example.com")

        val result = channel.deliver(createAlert())

        assertEquals(NotificationResult.Delivered, result)
        val sent = requireNotNull(adapter.lastSent)
        assertEquals("gateway@example.com", sent.source.address)
        assertEquals("ops@example.com", sent.destination.address)
        assertTrue(sent.payload.content.contains("Sprawdź kartę SIM"))
        assertTrue(sent.attributes.getValue("subject").contains("CRITICAL"))
    }

    @Test
    fun `deliver returns Failed when the underlying adapter throws`() {
        val adapter = RecordingAdapter(onSend = { throw IllegalStateException("SMTP timeout") })
        val channel = EmailNotificationChannel(adapter, fromAddress = "gateway@example.com", toAddress = "ops@example.com")

        val result = channel.deliver(createAlert())

        assertIs<NotificationResult.Failed>(result)
        assertEquals("SMTP timeout", (result as NotificationResult.Failed).reason)
    }

    @Test
    fun `the message correlationId matches the Alert's when present`() {
        val adapter = RecordingAdapter()
        val channel = EmailNotificationChannel(adapter, fromAddress = "gateway@example.com", toAddress = "ops@example.com")
        val alert = createAlert().copy(correlationId = midomail.domain.message.CorrelationId("correlation-1"))

        channel.deliver(alert)

        assertEquals("correlation-1", adapter.lastSent?.identity?.correlationId?.value)
    }
}
