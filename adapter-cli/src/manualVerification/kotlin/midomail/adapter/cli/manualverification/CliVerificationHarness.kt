package midomail.adapter.cli.manualverification

import midomail.adapter.cli.AdapterManagementCommands
import midomail.adapter.cli.CliDispatcher
import midomail.adapter.cli.ConfigurationCommands
import midomail.adapter.cli.ReadStateCommands
import midomail.adapter.cli.RoutingCommands
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.adapter.Registry
import midomail.domain.administration.EventBasedAdminAuditRecorder
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.event.EventCategory
import midomail.domain.event.SourceComponent
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.PageRequest
import midomail.domain.port.memory.InMemoryConfigurationProvider
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingConditions
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import midomail.domain.statistics.StatisticsAggregator
import kotlin.system.exitProcess

/**
 * Harness ręcznej weryfikacji Adaptera CLI end-to-end (Iteracja 5.17, docs/faza5-weryfikacja.md).
 * Ta sama kompozycja in-memory co harness REST (`:adapter-rest`) — dowód strukturalny, że oba
 * adaptery są cienkimi klientami identycznych portów administracyjnych (`:domain`), nawet
 * uruchamiane jako odrębne procesy JVM w tej fazie (prawdziwa, jednoprocesowa kompozycja
 * obu naraz pozostaje `:platform-jvm`, Fazą 7 — patrz Architectural Debt Report Fazy 5).
 *
 * Każdy krok wywołuje `CliDispatcher.dispatch(args: Array<String>)` z tablicą argumentów
 * skonstruowaną dokładnie tak, jak trafiłaby z powłoki do `main(args)`.
 *
 * Uruchomienie: `./gradlew :adapter-cli:runManualVerification`.
 */

private class SyntheticAdapter(override val adapterId: AdapterId, private val channelType: String) : Adapter {
    override val adapterVersion = "1.0"
    override fun start() {}
    override fun stop() {}
    override fun supportedChannels(): Set<Channel> = setOf(Channel(type = ChannelType(channelType)))
    override fun supportedCapabilities(): Set<Capability> = setOf(Capability.SUPPORTS_ATTACHMENTS)
    override fun health(): HealthStatus = HealthStatus(healthy = true)
    override fun metrics(): Metrics = Metrics(null, 0, 0, 7, 2, 0)
    override fun send(message: GatewayMessage) {}
}

private var passCount = 0
private var failCount = 0

private fun check(number: Int, name: String, condition: Boolean, details: String) {
    VerificationLog.scenarioResult(number, name, condition, details)
    if (condition) passCount++ else failCount++
}

fun main() {
    VerificationLog.info("=== Harness weryfikacji Adaptera CLI — START ===")

    val registry = Registry(InMemoryEventPublisher())
    val emailAdapter = SyntheticAdapter(AdapterId("email-primary"), "email")
    val gsmAdapter = SyntheticAdapter(AdapterId("gsm-primary"), "sms")
    registry.register(emailAdapter.adapterId, emailAdapter)
    registry.register(gsmAdapter.adapterId, gsmAdapter)
    val managedAdapters = ManagedAdapters(listOf(emailAdapter, gsmAdapter))

    val routingRuleAdministration = RoutingRuleAdministration(
        listOf(
            RoutingRule(
                ruleId = RuleId("sms-to-email"),
                priority = 100,
                conditions = RoutingConditions(sourceChannel = ChannelType("sms")),
                targetChannel = ChannelType("email"),
                targetAdapter = AdapterId("email-primary"),
                deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                version = RuleVersion("1")
            )
        )
    )
    val configurationProvider = InMemoryConfigurationProvider()
    val statisticsAggregator = StatisticsAggregator(listOf(emailAdapter, gsmAdapter), InMemorySchedulerProvider(), 60_000)
    val eventPublisher = InMemoryEventPublisher()
    val eventStore = InMemoryEventStore()
    val diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), eventStore, registry)
    val auditRecorder = EventBasedAdminAuditRecorder(eventPublisher, eventStore, SourceComponent("CliDispatcher"))

    val commands = ReadStateCommands(registry, managedAdapters, routingRuleAdministration, configurationProvider, statisticsAggregator, diagnosticsFacade).commands() +
        AdapterManagementCommands(registry, managedAdapters).commands() +
        ConfigurationCommands(configurationProvider).commands() +
        RoutingCommands(routingRuleAdministration).commands()
    val dispatcher = CliDispatcher(commands, auditRecorder)

    VerificationLog.scenarioStart(1, "Odczyt stanu: adapters listuje oba adaptery")
    val adaptersOutput = dispatcher.dispatch(arrayOf("adapters"))
    check(1, "adapters", adaptersOutput.contains("email-primary") && adaptersOutput.contains("gsm-primary"), adaptersOutput.replace("\n", " | "))

    VerificationLog.scenarioStart(2, "Zarządzanie adapterami: disable→DEGRADED, enable→READY")
    dispatcher.dispatch(arrayOf("adapter-disable", "gsm-primary"))
    val stateAfterDisable = registry.stateOf(AdapterId("gsm-primary"))
    dispatcher.dispatch(arrayOf("adapter-enable", "gsm-primary"))
    val stateAfterEnable = registry.stateOf(AdapterId("gsm-primary"))
    check(2, "adapter-disable/enable", stateAfterDisable == AdapterState.DEGRADED && stateAfterEnable == AdapterState.READY, "disable=$stateAfterDisable enable=$stateAfterEnable")

    VerificationLog.scenarioStart(3, "Zarządzanie adapterami: restart wywołuje cykl stop/start")
    val restartOutput = dispatcher.dispatch(arrayOf("adapter-restart", "gsm-primary"))
    check(3, "adapter-restart", restartOutput == "OK" && registry.stateOf(AdapterId("gsm-primary")) == AdapterState.READY, "output=$restartOutput")

    VerificationLog.scenarioStart(4, "Zarządzanie adapterami: adapter-add jawnie nieobsługiwane")
    val addOutput = dispatcher.dispatch(arrayOf("adapter-add"))
    check(4, "adapter-add", addOutput.contains("nie jest obsługiwane"), addOutput)

    VerificationLog.scenarioStart(5, "Konfiguracja: zapis, historia, rollback")
    dispatcher.dispatch(arrayOf("config-set", "gateway.instanceId", "midomail-01"))
    dispatcher.dispatch(arrayOf("config-set", "gateway.instanceId", "midomail-02"))
    val configOutput = dispatcher.dispatch(arrayOf("config", "gateway.instanceId"))
    val rollbackOutput = dispatcher.dispatch(arrayOf("config-rollback", "gateway.instanceId"))
    check(
        5, "config-set/config/config-rollback",
        configOutput.contains("midomail-02") && configOutput.contains("midomail-01") && rollbackOutput.contains("midomail-01"),
        "config=${configOutput.replace("\n", " | ")} rollback=$rollbackOutput"
    )

    VerificationLog.scenarioStart(6, "Routing: dodaj regułę, symuluj dopasowanie, usuń")
    dispatcher.dispatch(arrayOf("routing-add", "email-to-sms", "50", "sms", "gsm-primary", "AT_LEAST_ONCE"))
    val simulateOutput = dispatcher.dispatch(arrayOf("routing-simulate", "email", "sms"))
    dispatcher.dispatch(arrayOf("routing-remove", "email-to-sms"))
    check(
        6, "routing-add/simulate/remove",
        simulateOutput.contains("matched") && simulateOutput.contains("gsm-primary") && routingRuleAdministration.list().none { it.ruleId.value == "email-to-sms" },
        "simulate=$simulateOutput"
    )

    VerificationLog.scenarioStart(7, "Audyt: komendy zapisu zarejestrowane jako Event ADMINISTRATIVE, komendy odczytu nie")
    val auditEvents = eventStore.query(EventQueryFilter(category = EventCategory.ADMINISTRATIVE), PageRequest())
    check(7, "audyt ADMINISTRATIVE", auditEvents.items.isNotEmpty() && auditEvents.items.all { it.eventType.value == "admin.operation" }, "count=${auditEvents.items.size}")

    VerificationLog.info("=== Harness weryfikacji Adaptera CLI — KONIEC: PASS=$passCount FAIL=$failCount ===")
    if (failCount > 0) exitProcess(1)
}
