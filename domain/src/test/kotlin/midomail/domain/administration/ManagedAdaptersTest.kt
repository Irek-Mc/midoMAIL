package midomail.domain.administration

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Potwierdza `ManagedAdapters` (przeniesiony z `:adapter-rest` do `:domain` w Iteracji 5.14, żeby
 * `:adapter-rest`/`:adapter-cli` mogły współdzielić tę samą instancję — moduły siostrzane, żaden
 * nie zależy od drugiego).
 */
class ManagedAdaptersTest {

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion = "1.0"
        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 0, 0, 0)
        override fun send(message: GatewayMessage) {}
    }

    @Test
    fun `all returns every adapter provided at construction`() {
        val adapterA = FakeAdapter(AdapterId("email-primary"))
        val adapterB = FakeAdapter(AdapterId("gsm-primary"))

        val managedAdapters = ManagedAdapters(listOf(adapterA, adapterB))

        assertEquals(setOf(adapterA, adapterB), managedAdapters.all().toSet())
    }

    @Test
    fun `get returns the adapter with the matching AdapterId`() {
        val adapter = FakeAdapter(AdapterId("email-primary"))
        val managedAdapters = ManagedAdapters(listOf(adapter))

        assertEquals(adapter, managedAdapters.get(AdapterId("email-primary")))
    }

    @Test
    fun `get returns null for an unknown AdapterId`() {
        val managedAdapters = ManagedAdapters()

        assertNull(managedAdapters.get(AdapterId("unknown")))
    }

    @Test
    fun `remove drops the adapter from both get and all`() {
        val adapter = FakeAdapter(AdapterId("email-primary"))
        val managedAdapters = ManagedAdapters(listOf(adapter))

        managedAdapters.remove(AdapterId("email-primary"))

        assertNull(managedAdapters.get(AdapterId("email-primary")))
        assertEquals(emptyList(), managedAdapters.all())
    }
}
