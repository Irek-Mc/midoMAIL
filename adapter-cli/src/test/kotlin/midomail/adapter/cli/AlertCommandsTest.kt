package midomail.adapter.cli

import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.notification.EscalationScheduler
import midomail.domain.notification.NotificationChannel
import midomail.domain.notification.NotificationResult
import midomail.domain.notification.NotificationRouter
import midomail.domain.port.memory.InMemorySchedulerProvider
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertCommandsTest {

    private lateinit var scheduler: EscalationScheduler

    private class NoopChannel : NotificationChannel {
        override fun deliver(alert: Alert): NotificationResult = NotificationResult.Delivered
    }

    @AfterTest
    fun stopScheduler() {
        scheduler.stop()
    }

    private fun setup(): CliDispatcher {
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 60_000,
            escalateAfterMillis = { 60_000L },
            router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(NoopChannel())))
        )
        return CliDispatcher(AlertCommands(scheduler).commands())
    }

    private fun alert(id: String = "alert-1"): Alert = Alert(
        alertId = AlertId(id),
        level = AlertLevel.CRITICAL,
        source = SourceComponent("gsm-primary"),
        timestamp = Instant.now(),
        status = AlertStatus.ACTIVE
    )

    @Test
    fun `alerts command lists active alerts`() {
        val dispatcher = setup()
        scheduler.register(alert("alert-1"))

        val output = dispatcher.dispatch(arrayOf("alerts"))

        assertTrue(output.contains("alert-1"))
        assertTrue(output.contains("level=CRITICAL"))
    }

    @Test
    fun `alert-acknowledge removes the alert from the active list`() {
        val dispatcher = setup()
        scheduler.register(alert("alert-1"))

        val output = dispatcher.dispatch(arrayOf("alert-acknowledge", "alert-1"))

        assertEquals("OK", output)
        assertTrue(scheduler.activeAlerts().isEmpty())
    }

    @Test
    fun `alerts command with no active alerts reports so`() {
        val dispatcher = setup()

        val output = dispatcher.dispatch(arrayOf("alerts"))

        assertTrue(output.contains("Brak aktywnych alertów"))
    }
}
