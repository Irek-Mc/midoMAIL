package midomail.adapter.rest.manualverification

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.AdapterManagementEndpoints
import midomail.adapter.rest.AdminHttpServer
import midomail.adapter.rest.ConfigurationEndpoints
import midomail.adapter.rest.ReadStateEndpoints
import midomail.adapter.rest.RoutingEndpoints
import midomail.adapter.rest.dto.AdapterSummaryDto
import midomail.adapter.rest.dto.ConfigEntryDto
import midomail.adapter.rest.dto.RoutingDecisionDto
import midomail.adapter.rest.dto.RoutingRuleDto
import midomail.adapter.rest.dto.SimulateRoutingRequestDto
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.adapter.Registry
import midomail.domain.administration.EventBasedAdminAuditRecorder
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.administration.SecretStoreAdminAuthenticator
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.event.EventCategory
import midomail.domain.event.SourceComponent
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.PageRequest
import midomail.domain.port.SecretStore
import midomail.domain.port.memory.InMemoryConfigurationProvider
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import midomail.domain.statistics.StatisticsAggregator
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/**
 * Harness ręcznej weryfikacji Adaptera REST end-to-end (Iteracja 5.17, docs/faza5-weryfikacja.md).
 *
 * Kompozycja w pełni in-memory (real `Registry`/`RoutingRuleAdministration`/
 * `ConfigurationProvider`/`StatisticsAggregator`/`DiagnosticsFacade`, prawdziwy `AdminHttpServer`
 * na realnym porcie TCP, prawdziwy klient HTTP przez `HttpURLConnection`) — nie atrapa frameworka.
 * Dwie proste, syntetyczne instancje `Adapter` reprezentują adaptery kanałowe: harness weryfikuje
 * WARSTWĘ ADMINISTRACYJNĄ (SPEC-0024), nie ponownie łączność Gmail/GSM (już zweryfikowaną w
 * Fazach 2-3).
 *
 * Uruchomienie: `./gradlew :adapter-rest:runManualVerification`.
 */

private class InMemorySecretStore : SecretStore {
    private val values = ConcurrentHashMap<String, String>()
    override fun read(reference: String): String? = values[reference]
    override fun write(reference: String, value: String) {
        values[reference] = value
    }
}

private class SyntheticAdapter(override val adapterId: AdapterId, private val channelType: String) : Adapter {
    override val adapterVersion = "1.0"
    override fun start() {}
    override fun stop() {}
    override fun supportedChannels(): Set<Channel> = setOf(Channel(type = midomail.domain.message.ChannelType(channelType)))
    override fun supportedCapabilities(): Set<Capability> = setOf(Capability.SUPPORTS_ATTACHMENTS)
    override fun health(): HealthStatus = HealthStatus(healthy = true)
    override fun metrics(): Metrics = Metrics(null, 0, 0, 12, 4, 0)
    override fun send(message: GatewayMessage) {}
}

private var passCount = 0
private var failCount = 0

private fun check(number: Int, name: String, condition: Boolean, details: String) {
    VerificationLog.scenarioResult(number, name, condition, details)
    if (condition) passCount++ else failCount++
}

fun main() {
    VerificationLog.info("=== Harness weryfikacji Adaptera REST — START ===")

    val apiKey = "manual-verification-${System.currentTimeMillis()}"
    val secretStore = InMemorySecretStore().apply { write("admin-api/key", apiKey) }
    val authenticator = SecretStoreAdminAuthenticator(secretStore)

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
                conditions = midomail.domain.routing.RoutingConditions(sourceChannel = midomail.domain.message.ChannelType("sms")),
                targetChannel = midomail.domain.message.ChannelType("email"),
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
    val auditRecorder = EventBasedAdminAuditRecorder(eventPublisher, eventStore, SourceComponent("AdminHttpServer"))

    val server = AdminHttpServer(port = 0, authenticator = authenticator, auditRecorder = auditRecorder)
    ReadStateEndpoints(registry, managedAdapters, routingRuleAdministration, configurationProvider, statisticsAggregator, diagnosticsFacade).registerRoutes(server)
    AdapterManagementEndpoints(registry, managedAdapters).registerRoutes(server)
    ConfigurationEndpoints(configurationProvider).registerRoutes(server)
    RoutingEndpoints(routingRuleAdministration).registerRoutes(server)
    server.start()
    VerificationLog.info("Serwer administracyjny uruchomiony na porcie ${server.port()}")

    val json = Json
    val baseUrl = "http://localhost:${server.port()}"

    fun request(method: String, path: String, apiKeyHeader: String? = apiKey, body: String? = null): Pair<Int, String> {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        apiKeyHeader?.let { connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, it) }
        if (body != null || method != "GET") {
            connection.doOutput = true
            connection.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
        }
        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to responseBody
    }

    try {
        VerificationLog.scenarioStart(1, "Uwierzytelnianie: brak/zły klucz odrzucony 401")
        val (wrongKeyStatus, _) = request("GET", "/adapters", apiKeyHeader = "wrong-key")
        check(1, "Uwierzytelnianie odrzuca zły klucz", wrongKeyStatus == 401, "status=$wrongKeyStatus")

        VerificationLog.scenarioStart(2, "Odczyt stanu: GET /adapters zwraca oba adaptery")
        val (adaptersStatus, adaptersBody) = request("GET", "/adapters")
        val summaries = json.decodeFromString<List<AdapterSummaryDto>>(adaptersBody)
        check(2, "GET /adapters", adaptersStatus == 200 && summaries.size == 2, "status=$adaptersStatus count=${summaries.size}")

        VerificationLog.scenarioStart(3, "Zarządzanie adapterami: disable→DEGRADED, enable→READY")
        val (disableStatus, _) = request("POST", "/adapters/disable?id=gsm-primary")
        val stateAfterDisable = registry.stateOf(AdapterId("gsm-primary"))
        val (enableStatus, _) = request("POST", "/adapters/enable?id=gsm-primary")
        val stateAfterEnable = registry.stateOf(AdapterId("gsm-primary"))
        check(
            3, "disable/enable adaptera",
            disableStatus == 200 && enableStatus == 200 &&
                stateAfterDisable == midomail.domain.adapter.AdapterState.DEGRADED &&
                stateAfterEnable == midomail.domain.adapter.AdapterState.READY,
            "disable=$stateAfterDisable enable=$stateAfterEnable"
        )

        VerificationLog.scenarioStart(4, "Zarządzanie adapterami: restart wywołuje cykl stop/start")
        val (restartStatus, _) = request("POST", "/adapters/restart?id=gsm-primary")
        check(4, "restart adaptera", restartStatus == 200 && registry.stateOf(AdapterId("gsm-primary")) == midomail.domain.adapter.AdapterState.READY, "status=$restartStatus")

        VerificationLog.scenarioStart(5, "Zarządzanie adapterami: dodaj adapter jawnie 501")
        val (addStatus, addBody) = request("POST", "/adapters")
        check(5, "POST /adapters (dodaj) zwraca 501", addStatus == 501, "status=$addStatus body=$addBody")

        VerificationLog.scenarioStart(6, "Konfiguracja: zapis, historia, rollback")
        request("POST", "/config?key=gateway.instanceId", body = "midomail-01")
        val (setStatus2, setBody2) = request("POST", "/config?key=gateway.instanceId", body = "midomail-02")
        val afterSet = json.decodeFromString<ConfigEntryDto>(setBody2)
        val (rollbackStatus, rollbackBody) = request("POST", "/config/rollback?key=gateway.instanceId")
        val afterRollback = json.decodeFromString<ConfigEntryDto>(rollbackBody)
        check(
            6, "config set + rollback",
            setStatus2 == 200 && afterSet.value == "midomail-02" && afterSet.history == listOf("midomail-01") &&
                rollbackStatus == 200 && afterRollback.value == "midomail-01",
            "afterSet=${afterSet.value}/${afterSet.history} afterRollback=${afterRollback.value}"
        )

        VerificationLog.scenarioStart(7, "Routing: dodaj regułę, symuluj dopasowanie, usuń")
        val newRule = RoutingRuleDto(
            ruleId = "email-to-sms", priority = 50, enabled = true,
            conditions = midomail.adapter.rest.dto.RoutingConditionsDto(sourceChannel = "email"),
            targetChannel = "sms", targetAdapter = "gsm-primary", deliveryPolicy = "AT_LEAST_ONCE", version = "1"
        )
        val (addRuleStatus, _) = request("POST", "/routing/rules", body = json.encodeToString(newRule))
        val (simulateStatus, simulateBody) = request(
            "POST", "/routing/simulate",
            body = json.encodeToString(SimulateRoutingRequestDto(sourceChannel = "email", destinationChannel = "sms"))
        )
        val decision = json.decodeFromString<RoutingDecisionDto>(simulateBody)
        val (removeStatus, _) = request("DELETE", "/routing/rules?ruleId=email-to-sms")
        check(
            7, "routing add/simulate/remove",
            addRuleStatus == 200 && simulateStatus == 200 && decision.matched && decision.targetAdapter == "gsm-primary" &&
                removeStatus == 200 && routingRuleAdministration.list().none { it.ruleId.value == "email-to-sms" },
            "decision=$decision"
        )

        VerificationLog.scenarioStart(8, "Audyt: operacje zapisu i błąd uwierzytelnienia zarejestrowane jako Event ADMINISTRATIVE")
        val auditEvents = eventStore.query(EventQueryFilter(category = EventCategory.ADMINISTRATIVE), PageRequest())
        val hasAuthFailure = auditEvents.items.any { it.eventType.value.contains("auth_failure") }
        val hasWriteOperation = auditEvents.items.any { it.eventType.value == "admin.operation" }
        check(
            8, "audyt ADMINISTRATIVE",
            auditEvents.items.isNotEmpty() && hasAuthFailure && hasWriteOperation,
            "count=${auditEvents.items.size} hasAuthFailure=$hasAuthFailure hasWriteOperation=$hasWriteOperation"
        )
    } finally {
        server.stop()
    }

    VerificationLog.info("=== Harness weryfikacji Adaptera REST — KONIEC: PASS=$passCount FAIL=$failCount ===")
    if (failCount > 0) exitProcess(1)
}
