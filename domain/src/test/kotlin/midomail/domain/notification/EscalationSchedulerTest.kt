package midomail.domain.notification

import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.port.memory.InMemorySchedulerProvider
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza mechanizm eskalacji (38-Powiadomienia.md §5) — wyłącznie przez `SchedulerProvider`.
 */
class EscalationSchedulerTest {

    private lateinit var scheduler: EscalationScheduler

    @AfterTest
    fun stopScheduler() {
        if (::scheduler.isInitialized) scheduler.stop()
    }

    private class CountingChannel : NotificationChannel {
        val deliveries = AtomicInteger(0)
        override fun deliver(alert: Alert): NotificationResult {
            deliveries.incrementAndGet()
            return NotificationResult.Delivered
        }
    }

    private fun alert(alertId: String = "alert-1"): Alert = Alert(
        alertId = AlertId(alertId),
        level = AlertLevel.CRITICAL,
        source = SourceComponent("gsm-primary"),
        timestamp = Instant.now(),
        status = AlertStatus.ACTIVE
    )

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `an unacknowledged alert is re-delivered after the configured escalation threshold`() {
        val channel = CountingChannel()
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(channel)))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 30,
            escalateAfterMillis = { 100L },
            router = router
        )

        scheduler.register(alert())
        scheduler.start()

        val escalated = waitUntil(2_000) { channel.deliveries.get() >= 1 }
        assertTrue(escalated, "Alert powinien zostać ponownie dostarczony po przekroczeniu progu")
    }

    @Test
    fun `acknowledging an alert stops further escalation`() {
        val channel = CountingChannel()
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(channel)))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 30,
            escalateAfterMillis = { 60L },
            router = router
        )

        scheduler.register(alert())
        scheduler.start()
        waitUntil(2_000) { channel.deliveries.get() >= 1 }
        scheduler.acknowledge(AlertId("alert-1"))
        val countAtAck = channel.deliveries.get()
        Thread.sleep(300)

        assertEquals(countAtAck, channel.deliveries.get(), "Po potwierdzeniu nie powinny nastąpić dalsze eskalacje")
    }

    @Test
    fun `an alert level with no configured escalation threshold is never re-delivered`() {
        val channel = CountingChannel()
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(channel)))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 30,
            escalateAfterMillis = { null },
            router = router
        )

        scheduler.register(alert())
        scheduler.start()
        Thread.sleep(300)

        assertEquals(0, channel.deliveries.get())
    }

    @Test
    fun `only ACTIVE alerts are tracked for escalation`() {
        val channel = CountingChannel()
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(channel)))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 30,
            escalateAfterMillis = { 50L },
            router = router
        )

        scheduler.register(alert().copy(status = AlertStatus.RESOLVED))
        scheduler.start()
        Thread.sleep(300)

        assertEquals(0, channel.deliveries.get())
    }

    @Test
    fun `activeAlerts returns registered ACTIVE alerts being tracked for escalation`() {
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(CountingChannel())))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 1_000,
            escalateAfterMillis = { 1_000L },
            router = router
        )

        scheduler.register(alert("alert-1"))
        scheduler.register(alert("alert-2"))

        val ids = scheduler.activeAlerts().map { it.alertId.value }.toSet()
        assertEquals(setOf("alert-1", "alert-2"), ids)
    }

    @Test
    fun `activeAlerts includes a registered alert even when its level has no escalation threshold`() {
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(CountingChannel())))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 1_000,
            escalateAfterMillis = { null },
            router = router
        )

        scheduler.register(alert())

        assertEquals(listOf("alert-1"), scheduler.activeAlerts().map { it.alertId.value })
    }

    @Test
    fun `activeAlerts does not include an alert never passed to register`() {
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(CountingChannel())))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 1_000,
            escalateAfterMillis = { 1_000L },
            router = router
        )

        assertTrue(scheduler.activeAlerts().isEmpty())
    }

    @Test
    fun `acknowledge removes the alert from activeAlerts`() {
        val router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(CountingChannel())))
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 1_000,
            escalateAfterMillis = { 1_000L },
            router = router
        )
        scheduler.register(alert())

        scheduler.acknowledge(AlertId("alert-1"))

        assertTrue(scheduler.activeAlerts().isEmpty())
    }
}
