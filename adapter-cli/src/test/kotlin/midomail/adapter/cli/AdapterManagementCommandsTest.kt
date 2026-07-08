package midomail.adapter.cli

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemoryEventPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza komendy CLI zarządzania adapterami (SPEC-0024, §2).
 */
class AdapterManagementCommandsTest {

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion = "1.0"
        var startCount = 0
        var stopCount = 0
        override fun start() { startCount++ }
        override fun stop() { stopCount++ }
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 0, 0, 0)
        override fun send(message: GatewayMessage) {}
    }

    private fun setup(): Triple<Registry, ManagedAdapters, FakeAdapter> {
        val registry = Registry(InMemoryEventPublisher())
        val adapter = FakeAdapter(AdapterId("email-primary"))
        registry.register(AdapterId("email-primary"), adapter)
        return Triple(registry, ManagedAdapters(listOf(adapter)), adapter)
    }

    @Test
    fun `adapter-disable transitions to DEGRADED`() {
        val (registry, managedAdapters, _) = setup()
        val dispatcher = CliDispatcher(AdapterManagementCommands(registry, managedAdapters).commands())

        val output = dispatcher.dispatch(arrayOf("adapter-disable", "email-primary"))

        assertEquals("OK", output)
        assertEquals(AdapterState.DEGRADED, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `adapter-restart calls stop and start again on the same instance`() {
        val (registry, managedAdapters, adapter) = setup()
        val dispatcher = CliDispatcher(AdapterManagementCommands(registry, managedAdapters).commands())

        dispatcher.dispatch(arrayOf("adapter-restart", "email-primary"))

        assertEquals(1, adapter.stopCount)
        assertEquals(2, adapter.startCount)
        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `adapter-delete removes the adapter from Registry and ManagedAdapters`() {
        val (registry, managedAdapters, _) = setup()
        val dispatcher = CliDispatcher(AdapterManagementCommands(registry, managedAdapters).commands())

        dispatcher.dispatch(arrayOf("adapter-delete", "email-primary"))

        assertNull(registry.stateOf(AdapterId("email-primary")))
        assertNull(managedAdapters.get(AdapterId("email-primary")))
    }

    @Test
    fun `adapter-add is explicitly unsupported`() {
        val (registry, managedAdapters, _) = setup()
        val dispatcher = CliDispatcher(AdapterManagementCommands(registry, managedAdapters).commands())

        val output = dispatcher.dispatch(arrayOf("adapter-add"))

        assertTrue(output.contains("nie jest obsługiwane"))
    }
}
