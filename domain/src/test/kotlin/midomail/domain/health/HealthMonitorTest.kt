package midomail.domain.health

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemorySchedulerProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt agregacji zdrowia (SPEC-0020-Health-Aggregation-Contract.md).
 */
class HealthMonitorTest {

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion: String = "1.0"
        val healthy = AtomicBoolean(true)

        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = healthy.get())
        override fun metrics(): Metrics = Metrics(null, 0, 0, 0, 0, 0)
        override fun send(message: GatewayMessage) {}
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `no Alert is raised while every adapter stays healthy`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        val alerts = CopyOnWriteArrayList<Alert>()
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { alerts.add(it) }
        )

        monitor.start()
        Thread.sleep(200)

        assertTrue(alerts.isEmpty(), "Brak przejścia stanu - brak Alertu")
    }

    @Test
    fun `a healthy to unhealthy transition raises exactly one WARNING Alert`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        val alerts = CopyOnWriteArrayList<Alert>()
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { alerts.add(it) }
        )

        monitor.start()
        adapter.healthy.set(false)

        val raised = waitUntil(2_000) { alerts.isNotEmpty() }
        assertTrue(raised)
        Thread.sleep(150)

        assertEquals(1, alerts.size, "Dokładnie jeden Alert na przejście, nie jeden na cykl")
        assertEquals(AlertLevel.WARNING, alerts.single().level)
        assertEquals(AlertStatus.ACTIVE, alerts.single().status)
        assertEquals("adapter-1", alerts.single().source.value)
    }

    @Test
    fun `an unhealthy to healthy transition raises an INFO RESOLVED Alert`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        adapter.healthy.set(false)
        val alerts = CopyOnWriteArrayList<Alert>()
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { alerts.add(it) }
        )

        monitor.start()
        val firstAlertRaised = waitUntil(2_000) { alerts.isNotEmpty() }
        assertTrue(firstAlertRaised, "Adapter niezdrowy już przy pierwszym cyklu powinien wygenerować Alert")
        assertEquals(AlertStatus.ACTIVE, alerts.single().status)

        adapter.healthy.set(true)
        val resolvedRaised = waitUntil(2_000) { alerts.size == 2 }
        assertTrue(resolvedRaised)

        assertEquals(AlertLevel.INFO, alerts[1].level)
        assertEquals(AlertStatus.RESOLVED, alerts[1].status)
    }

    @Test
    fun `an adapter unhealthy from the very first check raises an Alert`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        adapter.healthy.set(false)
        val alerts = CopyOnWriteArrayList<Alert>()
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { alerts.add(it) }
        )

        monitor.start()

        val raised = waitUntil(2_000) { alerts.isNotEmpty() }
        assertTrue(raised)
        assertEquals(AlertStatus.ACTIVE, alerts.single().status)
    }

    @Test
    fun `stop cancels the scheduled check`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        val alerts = CopyOnWriteArrayList<Alert>()
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { alerts.add(it) }
        )

        monitor.start()
        monitor.stop()
        adapter.healthy.set(false)
        Thread.sleep(200)

        assertTrue(alerts.isEmpty(), "Po stop() nie powinny zachodzić dalsze sprawdzenia")
    }

    // --- currentStatus() (ADR-0034) ---

    @Test
    fun `currentStatus is READY before the first check, optimistic baseline`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 60_000,
            alertSink = AlertSink { }
        )

        assertEquals(AggregateHealthStatus.READY, monitor.currentStatus())
    }

    @Test
    fun `currentStatus is READY while every adapter is healthy`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        val monitor = HealthMonitor(
            adapters = listOf(adapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { }
        )
        monitor.start()

        waitUntil(2_000) { monitor.currentStatus() == AggregateHealthStatus.READY }

        assertEquals(AggregateHealthStatus.READY, monitor.currentStatus())
    }

    @Test
    fun `currentStatus becomes DEGRADED when at least one adapter becomes unhealthy`() {
        val healthyAdapter = FakeAdapter(AdapterId("adapter-1"))
        val unhealthyAdapter = FakeAdapter(AdapterId("adapter-2"))
        val monitor = HealthMonitor(
            adapters = listOf(healthyAdapter, unhealthyAdapter),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 50,
            alertSink = AlertSink { }
        )
        monitor.start()
        unhealthyAdapter.healthy.set(false)

        val degraded = waitUntil(2_000) { monitor.currentStatus() == AggregateHealthStatus.DEGRADED }

        assertTrue(degraded)
    }
}
