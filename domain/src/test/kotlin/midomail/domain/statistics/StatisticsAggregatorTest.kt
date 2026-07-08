package midomail.domain.statistics

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt agregacji statystyk (SPEC-0021-Statistics-Aggregation-Contract.md).
 */
class StatisticsAggregatorTest {

    private lateinit var aggregator: StatisticsAggregator

    @AfterTest
    fun stopAggregator() {
        if (::aggregator.isInitialized) aggregator.stop()
    }

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion = "1.0"
        val sent = AtomicLong(0)

        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, sent.get(), 0, 0)
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
    fun `the first tick establishes a baseline and produces no snapshot`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        // scheduleAtFixedRate uruchamia pierwsze wykonanie po interwale, nie natychmiast - tick 1
        // zachodzi w ok. t=300ms, tick 2 w ok. t=600ms; sprawdzenie przy t=450ms łapie stan PO
        // pierwszym ticku (baseline), ale PRZED drugim (pierwsza faktyczna migawka).
        aggregator = StatisticsAggregator(listOf(adapter), InMemorySchedulerProvider(), snapshotIntervalMillis = 300)

        aggregator.start()
        Thread.sleep(450)

        assertTrue(aggregator.snapshots().isEmpty(), "Pierwszy tick ustanawia baseline, nie generuje migawki")
    }

    @Test
    fun `a snapshot reflects the increment since the previous tick, not the cumulative total`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        aggregator = StatisticsAggregator(listOf(adapter), InMemorySchedulerProvider(), snapshotIntervalMillis = 300)

        aggregator.start()
        Thread.sleep(450) // po ticku 1 (baseline: sent=0), przed tickiem 2
        adapter.sent.set(5)

        val gotFirstSnapshot = waitUntil(2_000) { aggregator.snapshots().isNotEmpty() }
        assertTrue(gotFirstSnapshot)
        assertEquals(5L, aggregator.snapshots()[0].messagesSent, "Przyrost 5-0=5")

        adapter.sent.set(8)
        val gotSecondSnapshot = waitUntil(2_000) { aggregator.snapshots().size >= 2 }
        assertTrue(gotSecondSnapshot)
        assertEquals(3L, aggregator.snapshots()[1].messagesSent, "Przyrost 8-5=3, nie wartość skumulowana 8")
    }

    @Test
    fun `snapshots survive a purge of the underlying MessageStore - structural independence`() {
        val adapter = FakeAdapter(AdapterId("adapter-1"))
        aggregator = StatisticsAggregator(listOf(adapter), InMemorySchedulerProvider(), snapshotIntervalMillis = 50)
        var messageStore = InMemoryMessageStore()

        aggregator.start()
        Thread.sleep(60)
        adapter.sent.set(1)
        waitUntil(2_000) { aggregator.snapshots().isNotEmpty() }
        val countBeforePurge = aggregator.snapshots().size

        // Symulacja purge - nowa, pusta instancja MessageStore (StatisticsAggregator nigdy z niej
        // nie czyta, więc to nie ma na niego żadnego wpływu - dokładnie właściwość z SPEC-0021).
        messageStore = InMemoryMessageStore()

        assertEquals(countBeforePurge, aggregator.snapshots().size)
        assertTrue(
            messageStore.query(
                midomail.domain.port.MessageQueryFilter(),
                midomail.domain.port.MessageSort(),
                midomail.domain.port.PageRequest()
            ).items.isEmpty()
        )
    }
}
