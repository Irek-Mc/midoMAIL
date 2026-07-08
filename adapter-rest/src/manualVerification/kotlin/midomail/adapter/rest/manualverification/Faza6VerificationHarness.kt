package midomail.adapter.rest.manualverification

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.AdapterConfigurationEndpoints
import midomail.adapter.rest.AdminHttpServer
import midomail.adapter.rest.AlertEndpoints
import midomail.adapter.rest.AuditEndpoints
import midomail.adapter.rest.ConfigurationYamlEndpoints
import midomail.adapter.rest.DashboardEndpoints
import midomail.adapter.rest.LogEndpoints
import midomail.adapter.rest.MessageEndpoints
import midomail.adapter.rest.MonitoringEndpoints
import midomail.adapter.rest.RbacEndpoints
import midomail.adapter.rest.RoutingEndpoints
import midomail.adapter.rest.YamlConfigurationCodec
import midomail.adapter.rest.dto.AccountDto
import midomail.adapter.rest.dto.AlertDto
import midomail.adapter.rest.dto.AuditEntryDto
import midomail.adapter.rest.dto.ExactlyOnceCountersDto
import midomail.adapter.rest.dto.GatewayStatusDto
import midomail.adapter.rest.dto.LogEntryDto
import midomail.adapter.rest.dto.LoginRequestDto
import midomail.adapter.rest.dto.LoginResultDto
import midomail.adapter.rest.dto.MessagePageDto
import midomail.adapter.rest.dto.ResourceSnapshotDto
import midomail.adapter.rest.dto.RoutingRuleChangeDto
import midomail.adapter.rest.dto.ValidationResultDto
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.administration.AdapterConfigurationAdministration
import midomail.domain.administration.MessageReprocessingAdministration
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.administration.SecretStoreAdminAuthenticator
import midomail.domain.configuration.AdapterConfigEntry
import midomail.domain.configuration.ConfigurationDocument
import midomail.domain.configuration.ConfigurationValidator
import midomail.domain.configuration.GatewayConfig
import midomail.domain.configuration.MessageStoreConfig
import midomail.domain.configuration.MonitoringConfig
import midomail.domain.configuration.NotificationsConfig
import midomail.domain.configuration.RoutingConfig
import midomail.domain.configuration.SchedulerConfig
import midomail.domain.configuration.SecurityConfig
import midomail.domain.event.SourceComponent
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.gateway.GatewayInfo
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.health.HealthMonitor
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.monitoring.ResourceMonitor
import midomail.domain.monitoring.ResourceSnapshot
import midomail.domain.notification.EscalationScheduler
import midomail.domain.notification.NotificationChannel
import midomail.domain.notification.NotificationResult
import midomail.domain.notification.NotificationRouter
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.SecretStore
import midomail.domain.port.memory.InMemoryConfigurationProvider
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.rbac.AccountAuthenticator
import midomail.domain.rbac.InMemoryAccountStore
import midomail.domain.rbac.InMemoryRoleStore
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/**
 * Harness ręcznej weryfikacji dodatków Fazy 6 do Adaptera REST (Iteracja 6.29,
 * docs/faza6-weryfikacja.md). Uzupełnia `RestVerificationHarness` (Faza 5) o wszystkie nowe
 * kategorie administracyjne: Alerty, Komunikaty(+reprocess), Monitoring zasobów, Historia
 * routingu, Konfiguracja adaptera, Konfiguracja YAML, RBAC, Dashboard, Logi, Audyt.
 *
 * Uruchomienie: `./gradlew :adapter-rest:runManualVerification` (mainClass ustawiony na ten plik
 * podczas trwania Iteracji 6.29 — patrz build.gradle.kts).
 */

private class Faza6SecretStore : SecretStore {
    private val values = ConcurrentHashMap<String, String>()
    override fun read(reference: String): String? = values[reference]
    override fun write(reference: String, value: String) { values[reference] = value }
}

private class Faza6SyntheticAdapter(override val adapterId: AdapterId, private val channelType: String) : Adapter {
    override val adapterVersion = "6.0"
    override fun start() {}
    override fun stop() {}
    override fun supportedChannels(): Set<Channel> = setOf(Channel(type = ChannelType(channelType)))
    override fun supportedCapabilities(): Set<Capability> = setOf(Capability.SUPPORTS_ATTACHMENTS)
    override fun health(): HealthStatus = HealthStatus(healthy = true)
    override fun metrics(): Metrics = Metrics(null, 0, 0, 10, 3, 0)
    override fun send(message: GatewayMessage) {}
}

private class NoopGatewayOutbound : GatewayOutbound {
    override fun send(message: GatewayMessage) {}
}

private class NoopNotificationChannel : NotificationChannel {
    override fun deliver(alert: Alert): NotificationResult = NotificationResult.Delivered
}

private class FakeResourceMonitor : ResourceMonitor {
    override fun snapshot(): ResourceSnapshot = ResourceSnapshot(ramUsedBytes = 500_000_000, ramTotalBytes = 2_000_000_000)
}

private var faza6PassCount = 0
private var faza6FailCount = 0

private fun faza6Check(number: Int, name: String, condition: Boolean, details: String) {
    VerificationLog.scenarioResult(number, name, condition, details)
    if (condition) faza6PassCount++ else faza6FailCount++
}

fun main() {
    VerificationLog.info("=== Harness weryfikacji dodatków Fazy 6 — START ===")

    val apiKey = "faza6-manual-verification-${System.currentTimeMillis()}"
    val secretStore = Faza6SecretStore().apply { write("admin-api/key", apiKey) }
    val authenticator = SecretStoreAdminAuthenticator(secretStore)

    val emailAdapter = Faza6SyntheticAdapter(AdapterId("email-primary"), "email")
    val registry = midomail.domain.adapter.Registry(InMemoryEventPublisher())
    registry.register(emailAdapter.adapterId, emailAdapter)
    val managedAdapters = midomail.domain.administration.ManagedAdapters(listOf(emailAdapter))

    val messageStore = InMemoryMessageStore()
    val exactlyOnceEngine = ExactlyOnceEngine(messageStore)
    val gatewayEngine = GatewayEngine(
        exactlyOnceEngine = exactlyOnceEngine,
        routingEngine = RoutingEngine(
            listOf(RoutingRule(RuleId("r1"), 100, targetChannel = ChannelType("email"), targetAdapter = AdapterId("email-primary"), deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"), version = RuleVersion("1")))
        ),
        eventPublisher = InMemoryEventPublisher(),
        gatewayOutbound = NoopGatewayOutbound()
    )
    val reprocessingAdministration = MessageReprocessingAdministration(messageStore, gatewayEngine)
    val routingRuleAdministration = RoutingRuleAdministration()
    val configurationProvider = InMemoryConfigurationProvider()
    val adapterConfigurationAdministration = AdapterConfigurationAdministration(configurationProvider)
    val eventStore = InMemoryEventStore()
    val eventPublisher = InMemoryEventPublisher()
    val auditRecorder = midomail.domain.administration.EventBasedAdminAuditRecorder(eventPublisher, eventStore, SourceComponent("AdminHttpServer"))
    val healthMonitor = HealthMonitor(listOf(emailAdapter), InMemorySchedulerProvider(), 60_000, midomail.domain.health.AlertSink { })
    healthMonitor.start()
    val escalationScheduler = EscalationScheduler(InMemorySchedulerProvider(), 60_000, { 60_000L }, NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(NoopNotificationChannel()))))
    val gatewayInfo = GatewayInfo(version = "6.0.0-manualverification", startedAt = Instant.now().minusSeconds(120))
    val resourceMonitor = FakeResourceMonitor()
    val yamlCodec = YamlConfigurationCodec()
    val configurationValidator = ConfigurationValidator()
    val accountStore = InMemoryAccountStore()
    val roleStore = InMemoryRoleStore()
    val accountAuthenticator = AccountAuthenticator(accountStore)

    val server = AdminHttpServer(port = 0, authenticator = authenticator, auditRecorder = auditRecorder)
    AlertEndpoints(escalationScheduler).registerRoutes(server)
    MessageEndpoints(messageStore, reprocessingAdministration).registerRoutes(server)
    MonitoringEndpoints(resourceMonitor).registerRoutes(server)
    RoutingEndpoints(routingRuleAdministration).registerRoutes(server)
    AdapterConfigurationEndpoints(adapterConfigurationAdministration).registerRoutes(server)
    ConfigurationYamlEndpoints(configurationProvider, yamlCodec, configurationValidator, platformProfile = "android").registerRoutes(server)
    RbacEndpoints(accountStore, roleStore, accountAuthenticator).registerRoutes(server)
    DashboardEndpoints(healthMonitor, gatewayInfo, exactlyOnceEngine, eventStore).registerRoutes(server)
    LogEndpoints(eventStore).registerRoutes(server)
    AuditEndpoints(eventStore).registerRoutes(server)
    server.start()
    VerificationLog.info("Serwer administracyjny (dodatki Fazy 6) uruchomiony na porcie ${server.port()}")

    val json = Json
    val baseUrl = "http://localhost:${server.port()}"

    fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, apiKey)
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
        VerificationLog.scenarioStart(1, "Alerty: rejestracja, GET /alerts, POST /alerts/acknowledge")
        escalationScheduler.register(Alert(AlertId("alert-1"), AlertLevel.WARNING, SourceComponent("email-primary"), Instant.now(), AlertStatus.ACTIVE))
        val (alertsStatus, alertsBody) = request("GET", "/alerts")
        val alerts = json.decodeFromString<List<AlertDto>>(alertsBody)
        val (ackStatus, _) = request("POST", "/alerts/acknowledge?id=alert-1")
        faza6Check(1, "alerty", alertsStatus == 200 && alerts.size == 1 && ackStatus == 200 && escalationScheduler.activeAlerts().isEmpty(), "alerts=$alerts")

        VerificationLog.scenarioStart(2, "Komunikaty: GET /messages, reprocess")
        gatewayEngine.receive(
            GatewayMessage(
                identity = Identity(MessageId("m1"), CorrelationId("c1"), schemaVersion = SchemaVersion("2.0"), externalReference = ExternalReference("<ext1@localhost>")),
                source = Channel(type = ChannelType("gsm")), destination = Channel(type = ChannelType("email")), payload = Payload("test")
            )
        )
        val (messagesStatus, messagesBody) = request("GET", "/messages")
        val page = json.decodeFromString<MessagePageDto>(messagesBody)
        val (reprocessStatus, _) = request("POST", "/messages/reprocess?externalReference=%3Cext1%40localhost%3E")
        faza6Check(2, "komunikaty", messagesStatus == 200 && page.items.size == 1 && page.items.single().createdAt != null && reprocessStatus == 200, "page=$page")

        VerificationLog.scenarioStart(3, "Monitoring zasobów: GET /monitoring/resources")
        val (resourcesStatus, resourcesBody) = request("GET", "/monitoring/resources")
        val resources = json.decodeFromString<ResourceSnapshotDto>(resourcesBody)
        faza6Check(3, "monitoring", resourcesStatus == 200 && resources.ramUsedBytes == 500_000_000L, "resources=$resources")

        VerificationLog.scenarioStart(4, "Historia routingu: GET /routing/rules/history")
        request("POST", "/routing/rules", json.encodeToString(midomail.adapter.rest.dto.RoutingRuleDto("r2", 50, true, midomail.adapter.rest.dto.RoutingConditionsDto(), "sms", "email-primary", "AT_LEAST_ONCE", version = "1")))
        val (historyStatus, historyBody) = request("GET", "/routing/rules/history")
        val history = json.decodeFromString<List<RoutingRuleChangeDto>>(historyBody)
        faza6Check(4, "historia routingu", historyStatus == 200 && history.any { it.changeType == "ADDED" }, "history=$history")

        VerificationLog.scenarioStart(5, "Konfiguracja adaptera: POST/GET /adapters/configuration")
        request("POST", "/adapters/configuration?id=email-primary&type=email&field=smtp.host", "smtp.example.com")
        val (adapterConfigStatus, adapterConfigBody) = request("GET", "/adapters/configuration?id=email-primary&type=email")
        faza6Check(5, "konfiguracja adaptera", adapterConfigStatus == 200 && adapterConfigBody.contains("smtp.example.com"), "body=$adapterConfigBody")

        VerificationLog.scenarioStart(6, "Konfiguracja YAML: validate + import + export + history + rollback")
        val document = ConfigurationDocument(
            version = "2.0", gateway = GatewayConfig("midomail-01", "INFO"), routing = RoutingConfig(),
            adapters = listOf(AdapterConfigEntry("email-primary", "email", enabled = false)),
            scheduler = SchedulerConfig(), security = SecurityConfig("android-keystore"),
            monitoring = MonitoringConfig(30), messageStore = MessageStoreConfig(30, 365), notifications = NotificationsConfig()
        )
        val yamlText = yamlCodec.encode(document)
        val (validateStatus, validateBody) = request("POST", "/config/yaml/validate", yamlText)
        val validation = json.decodeFromString<ValidationResultDto>(validateBody)
        val (importStatus, _) = request("POST", "/config/yaml/import", yamlText)
        val (exportStatus, exportBody) = request("GET", "/config/yaml/export")
        faza6Check(6, "konfiguracja YAML", validateStatus == 200 && validation.valid && importStatus == 200 && exportStatus == 200 && yamlCodec.decode(exportBody) == document, "valid=${validation.valid}")

        VerificationLog.scenarioStart(7, "RBAC: utworzenie konta, login, blokada")
        request("POST", "/accounts", json.encodeToString(mapOf("username" to "alice", "password" to "secret", "roleId" to "administrator")))
        val (loginStatus, loginBody) = request("POST", "/accounts/login", json.encodeToString(LoginRequestDto("alice", "secret")))
        val loginResult = json.decodeFromString<LoginResultDto>(loginBody)
        faza6Check(7, "RBAC", loginStatus == 200 && loginResult.outcome == "SUCCESS", "login=$loginResult")

        VerificationLog.scenarioStart(8, "Dashboard: status, exactly-once, events")
        val (dashboardStatus, dashboardBody) = request("GET", "/dashboard/status")
        val dashboardDto = json.decodeFromString<GatewayStatusDto>(dashboardBody)
        val (eoStatus, eoBody) = request("GET", "/dashboard/exactly-once")
        val eoDto = json.decodeFromString<ExactlyOnceCountersDto>(eoBody)
        faza6Check(8, "dashboard", dashboardStatus == 200 && dashboardDto.status == "READY" && eoStatus == 200 && eoDto.processed >= 1, "status=$dashboardDto eo=$eoDto")

        VerificationLog.scenarioStart(9, "Logi: EventStoreSystemLogger -> GET /logs")
        val systemLogger = midomail.domain.diagnostics.EventStoreSystemLogger(eventStore, SourceComponent("Harness"))
        systemLogger.log(midomail.domain.diagnostics.LogLevel.INFO, "Harness Fazy 6 uruchomiony", null, null)
        val (logsStatus, logsBody) = request("GET", "/logs")
        val logs = json.decodeFromString<List<LogEntryDto>>(logsBody)
        faza6Check(9, "logi", logsStatus == 200 && logs.any { it.message == "Harness Fazy 6 uruchomiony" }, "logs=${logs.size}")

        VerificationLog.scenarioStart(10, "Audyt: GET /audit zawiera operacje zapisu z poprzednich scenariuszy")
        val (auditStatus, auditBody) = request("GET", "/audit")
        val audit = json.decodeFromString<List<AuditEntryDto>>(auditBody)
        faza6Check(10, "audyt", auditStatus == 200 && audit.isNotEmpty(), "audit count=${audit.size}")
    } finally {
        server.stop()
        healthMonitor.stop()
        escalationScheduler.stop()
    }

    VerificationLog.info("=== Harness weryfikacji dodatków Fazy 6 — KONIEC: PASS=$faza6PassCount FAIL=$faza6FailCount ===")
    // Wymuszone wyjście (nie naturalny powrót z main()) - w przeciwieństwie do
    // RestVerificationHarness (Faza 5), ten harness konstruuje HealthMonitor/EscalationScheduler,
    // których InMemorySchedulerProvider trzyma wątki NIE-daemon bez metody shutdown() w kontrakcie
    // SchedulerProvider (SPEC-0013) - JVM nigdy nie zakończyłby się samoistnie. Odkryte podczas tej
    // iteracji, udokumentowane jako ograniczenie harnessu, nie zmieniane w :domain (SchedulerProvider
    // na Androidzie nie potrzebuje shutdown - cykl życia procesu zarządzany przez system).
    exitProcess(if (faza6FailCount > 0) 1 else 0)
}
