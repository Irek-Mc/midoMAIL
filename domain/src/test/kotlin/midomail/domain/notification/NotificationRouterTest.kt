package midomail.domain.notification

import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza routing Alert → kanały (38-Powiadomienia.md §4).
 */
class NotificationRouterTest {

    private class RecordingChannel : NotificationChannel {
        var deliveredCount = 0
        override fun deliver(alert: Alert): NotificationResult {
            deliveredCount++
            return NotificationResult.Delivered
        }
    }

    private fun alert(level: AlertLevel): Alert = Alert(
        alertId = AlertId("alert-1"),
        level = level,
        source = SourceComponent("gsm-primary"),
        timestamp = Instant.now(),
        status = AlertStatus.ACTIVE
    )

    @Test
    fun `CRITICAL routes to both configured channels`() {
        val webhook = RecordingChannel()
        val email = RecordingChannel()
        val router = NotificationRouter(
            mapOf(
                AlertLevel.CRITICAL to listOf(webhook, email),
                AlertLevel.WARNING to listOf(email)
            )
        )

        val results = router.route(alert(AlertLevel.CRITICAL))

        assertEquals(1, webhook.deliveredCount)
        assertEquals(1, email.deliveredCount)
        assertEquals(2, results.size)
        assertTrue(results.all { it == NotificationResult.Delivered })
    }

    @Test
    fun `WARNING routes only to email, not to webhook`() {
        val webhook = RecordingChannel()
        val email = RecordingChannel()
        val router = NotificationRouter(
            mapOf(
                AlertLevel.CRITICAL to listOf(webhook, email),
                AlertLevel.WARNING to listOf(email)
            )
        )

        router.route(alert(AlertLevel.WARNING))

        assertEquals(0, webhook.deliveredCount)
        assertEquals(1, email.deliveredCount)
    }

    @Test
    fun `INFO with no configured channels routes to nothing and returns an empty result list`() {
        val email = RecordingChannel()
        val router = NotificationRouter(mapOf(AlertLevel.WARNING to listOf(email)))

        val results = router.route(alert(AlertLevel.INFO))

        assertEquals(0, email.deliveredCount)
        assertEquals(emptyList(), results)
    }
}
