package midomail.adapter.cli

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemoryConfigurationProvider
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.statistics.StatisticsAggregator
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Potwierdza komendy odczytu stanu CLI (SPEC-0024, §1) — rzeczywiste wywołanie `dispatch(args)`.
 */
class ReadStateCommandsTest {

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion = "1.0"
        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 3, 1, 0)
        override fun send(message: GatewayMessage) {}
    }

    private fun buildDispatcher(registry: Registry, managedAdapters: ManagedAdapters): CliDispatcher {
        val commands = ReadStateCommands(
            registry = registry,
            managedAdapters = managedAdapters,
            routingRuleAdministration = RoutingRuleAdministration(),
            configurationProvider = InMemoryConfigurationProvider(),
            statisticsAggregator = StatisticsAggregator(managedAdapters.all(), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
        ).commands()
        return CliDispatcher(commands)
    }

    @Test
    fun `adapters command lists registered adapters with state and metrics`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapter = FakeAdapter(AdapterId("email-primary"))
        registry.register(AdapterId("email-primary"), adapter)
        val dispatcher = buildDispatcher(registry, ManagedAdapters(listOf(adapter)))

        val output = dispatcher.dispatch(arrayOf("adapters"))

        assertTrue(output.contains("email-primary"))
        assertTrue(output.contains("state=READY"))
        assertTrue(output.contains("sent=3"))
    }

    @Test
    fun `adapters command with an id argument filters to a single adapter`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapterA = FakeAdapter(AdapterId("email-primary"))
        val adapterB = FakeAdapter(AdapterId("gsm-primary"))
        registry.register(AdapterId("email-primary"), adapterA)
        registry.register(AdapterId("gsm-primary"), adapterB)
        val dispatcher = buildDispatcher(registry, ManagedAdapters(listOf(adapterA, adapterB)))

        val output = dispatcher.dispatch(arrayOf("adapters", "gsm-primary"))

        assertTrue(output.contains("gsm-primary"))
        assertTrue(!output.contains("email-primary"))
    }

    @Test
    fun `config command reports the current value and history`() {
        val configurationProvider = InMemoryConfigurationProvider()
        configurationProvider.setValue("gateway.instanceId", "v1")
        configurationProvider.setValue("gateway.instanceId", "v2")
        val registry = Registry(InMemoryEventPublisher())
        val dispatcher = CliDispatcher(
            ReadStateCommands(
                registry = registry,
                managedAdapters = ManagedAdapters(),
                routingRuleAdministration = RoutingRuleAdministration(),
                configurationProvider = configurationProvider,
                statisticsAggregator = StatisticsAggregator(emptyList(), InMemorySchedulerProvider(), 60_000),
                diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
            ).commands()
        )

        val output = dispatcher.dispatch(arrayOf("config", "gateway.instanceId"))

        assertTrue(output.contains("v2"))
        assertTrue(output.contains("v1"))
    }

    @Test
    fun `config command without a key argument returns a usage message`() {
        val registry = Registry(InMemoryEventPublisher())
        val dispatcher = CliDispatcher(
            ReadStateCommands(
                registry = registry,
                managedAdapters = ManagedAdapters(),
                routingRuleAdministration = RoutingRuleAdministration(),
                configurationProvider = InMemoryConfigurationProvider(),
                statisticsAggregator = StatisticsAggregator(emptyList(), InMemorySchedulerProvider(), 60_000),
                diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
            ).commands()
        )

        val output = dispatcher.dispatch(arrayOf("config"))

        assertTrue(output.contains("Użycie"))
    }
}
