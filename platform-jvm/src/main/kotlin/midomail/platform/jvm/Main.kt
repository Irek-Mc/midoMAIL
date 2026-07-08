package midomail.platform.jvm

import midomail.adapter.cli.AdapterConfigurationCommands
import midomail.adapter.cli.AdapterManagementCommands
import midomail.adapter.cli.AlertCommands
import midomail.adapter.cli.CliDispatcher
import midomail.adapter.cli.ConfigurationCommands
import midomail.adapter.cli.MessageCommands
import midomail.adapter.cli.MonitoringCommands
import midomail.adapter.cli.RbacCommands
import midomail.adapter.cli.ReadStateCommands
import midomail.adapter.cli.RoutingCommands
import midomail.adapter.email.EmailAdapterConfiguration
import midomail.adapter.email.EmailAdapterFactory
import midomail.adapter.email.ImapConfig
import midomail.adapter.email.SmtpConfig
import midomail.adapter.rest.AdapterConfigurationEndpoints
import midomail.adapter.rest.AdapterManagementEndpoints
import midomail.adapter.rest.AdminHttpServer
import midomail.adapter.rest.AlertEndpoints
import midomail.adapter.rest.AuditEndpoints
import midomail.adapter.rest.ConfigurationEndpoints
import midomail.adapter.rest.ConfigurationYamlEndpoints
import midomail.adapter.rest.DashboardEndpoints
import midomail.adapter.rest.LogEndpoints
import midomail.adapter.rest.MessageEndpoints
import midomail.adapter.rest.MonitoringEndpoints
import midomail.adapter.rest.ReadStateEndpoints
import midomail.adapter.rest.RbacEndpoints
import midomail.adapter.rest.RoutingEndpoints
import midomail.adapter.rest.YamlConfigurationCodec
import midomail.adapter.websocket.ReconnectPolicy
import midomail.adapter.websocket.WebSocketAdapterConfiguration
import midomail.adapter.websocket.WebSocketAdapterFactory
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.adapter.Registry
import midomail.domain.administration.AdapterConfigurationAdministration
import midomail.domain.administration.EventBasedAdminAuditRecorder
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.MessageReprocessingAdministration
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.administration.SecretStoreAdminAuthenticator
import midomail.domain.configuration.ConfigurationDocument
import midomail.domain.configuration.ConfigurationValidator
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.diagnostics.EventStoreSystemLogger
import midomail.domain.event.SourceComponent
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.gateway.GatewayInfo
import midomail.domain.health.AlertSink
import midomail.domain.health.HealthMonitor
import midomail.domain.notification.EscalationScheduler
import midomail.domain.notification.NotificationRouter
import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.rbac.InMemoryAccountStore
import midomail.domain.rbac.InMemoryRoleStore
import midomail.domain.rbac.AccountAuthenticator
import midomail.domain.routing.RoutingEngine
import midomail.domain.statistics.StatisticsAggregator
import midomail.ui.web.StaticFileServer
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path as toPath

/**
 * Uchwyt na w pełni złożony graf `:platform-jvm` (Iteracja 7.7/7.8) — zwracany przez [buildGateway],
 * żeby kompozycja była testowalna wprost (Iteracja 7.8), nie wyłącznie przez uruchomienie całego
 * procesu `main()`.
 */
class PlatformJvmGateway(
    val adminServer: AdminHttpServer,
    val uiServer: StaticFileServer,
    val cliDispatcher: CliDispatcher,
    val registry: Registry,
    val registeredAdapters: List<Adapter>,
    val apiKey: String,
    private val healthMonitor: HealthMonitor,
    private val statisticsAggregator: StatisticsAggregator,
    private val escalationScheduler: EscalationScheduler
) {
    fun stop() {
        adminServer.stop()
        uiServer.stop()
        healthMonitor.stop()
        statisticsAggregator.stop()
        escalationScheduler.stop()
        registeredAdapters.forEach { it.stop() }
    }
}

data class ProcessOptions(
    val configPath: String = "./config.properties",
    val secretsPath: String = "./secrets.properties",
    val adminPort: Int = 8080,
    val uiPort: Int = 8081
)

/**
 * Punkt kompozycyjny główny `:platform-jvm` (ADR-0036-Platforma-JVM-Kompozycja.md) — dowód
 * przenośności Roadmapy §9: identyczny model domenowy i Gateway Engine, na drugiej platformie
 * (JVM/Linux, nie Android), z Email + WebSocket + REST + CLI + UI w JEDNYM procesie.
 */
fun buildGateway(options: ProcessOptions): PlatformJvmGateway {
    val secretStore = FileSecretStore(toPath(options.secretsPath))
    val configurationProvider = FileConfigurationProvider(toPath(options.configPath))
    val yamlCodec = YamlConfigurationCodec()
    val configurationValidator = ConfigurationValidator()

    val eventStore = InMemoryEventStore()
    val eventPublisher = InMemoryEventPublisher()
    val messageStore = InMemoryMessageStore()
    val systemLogger = EventStoreSystemLogger(eventStore, SourceComponent("platform-jvm"))
    val schedulerProvider: SchedulerProvider = InMemorySchedulerProvider()

    val startupDocument = loadStartupDocument(configurationProvider, yamlCodec)
    val routingRuleAdministration = RoutingRuleAdministration(startupDocument?.routing?.rules.orEmpty().map { it.toRoutingRule() })
    val outbound = AdapterRegistryOutbound()

    var lastRulesUsed = routingRuleAdministration.list()
    val exactlyOnceEngine = ExactlyOnceEngine(messageStore)
    val gatewayEngineRef = AtomicReference(
        GatewayEngine(exactlyOnceEngine, RoutingEngine(lastRulesUsed), eventPublisher, outbound)
    )
    val gatewayInbound = SwappableGatewayInbound(gatewayEngineRef.get())

    // Amendment ADR-0036: propagacja zmian reguł do żywego GatewayEngine bez modyfikacji
    // GatewayEngine/RoutingEngine (oba pozostają nietknięte). Odpytywanie co 5s - świadomy
    // kompromis (ten sam duch co SA-11, Faza 6).
    schedulerProvider.schedule(TaskId("platform-jvm-routing-sync"), 5000) {
        val currentRules = routingRuleAdministration.list()
        if (currentRules != lastRulesUsed) {
            lastRulesUsed = currentRules
            val rebuilt = GatewayEngine(exactlyOnceEngine, RoutingEngine(currentRules), eventPublisher, outbound)
            gatewayEngineRef.set(rebuilt)
            gatewayInbound.delegate = rebuilt
            systemLogger.log(midomail.domain.diagnostics.LogLevel.INFO, "Reguły routingu zmienione - GatewayEngine przebudowany (${currentRules.size} reguł)", null, null)
        }
    }

    val ports = AdapterPorts(
        gatewayInbound = gatewayInbound,
        messageStore = messageStore,
        logger = object : Logger {
            override fun info(message: String) = println("[INFO] $message")
            override fun warn(message: String, throwable: Throwable?) = println("[WARN] $message ${throwable?.message ?: ""}")
            override fun error(message: String, throwable: Throwable?) = println("[ERROR] $message ${throwable?.message ?: ""}")
        },
        healthReporter = object : HealthReporter {
            override fun report(adapterId: AdapterId, status: HealthStatus) {}
        },
        attachmentStore = InMemoryAttachmentStore(),
        eventPublisher = eventPublisher
    )

    val registry = Registry(eventPublisher)
    val registeredAdapters = mutableListOf<Adapter>()

    val emailAdapter = createEmailAdapterIfConfigured(secretStore, ports)
    if (emailAdapter != null) {
        outbound.register(emailAdapter)
        if (registerSafely(registry, emailAdapter)) registeredAdapters.add(emailAdapter)
    } else {
        println("[INFO] Poświadczenia email nie skonfigurowane (secrets: email-primary/username|password) - EmailAdapter nie zarejestrowany")
    }

    val webSocketAdapter = createWebSocketAdapterIfConfigured(configurationProvider, yamlCodec, ports, schedulerProvider)
    if (webSocketAdapter != null) {
        outbound.register(webSocketAdapter)
        if (registerSafely(registry, webSocketAdapter)) registeredAdapters.add(webSocketAdapter)
    } else {
        println("[INFO] Adapter WebSocket nie skonfigurowany (gateway.fullConfiguration, adapter type=websocket) - nie zarejestrowany")
    }

    val managedAdapters = ManagedAdapters(registeredAdapters)
    val diagnosticsFacade = DiagnosticsFacade(messageStore, eventStore, registry)
    val reprocessingAdministration = MessageReprocessingAdministration(messageStore, gatewayInbound)
    val adapterConfigurationAdministration = AdapterConfigurationAdministration(configurationProvider)
    val resourceMonitor = JvmResourceMonitor(toPath(options.configPath).toAbsolutePath().parent ?: toPath("."))

    val accountStore = InMemoryAccountStore()
    val roleStore = InMemoryRoleStore()
    val accountAuthenticator = AccountAuthenticator(accountStore)

    val notificationRouter = NotificationRouter(emptyMap())
    val escalationScheduler = EscalationScheduler(schedulerProvider, 60_000, ::escalationThresholdFor, notificationRouter)
    escalationScheduler.start()
    val alertSink = AlertSink { alert -> notificationRouter.route(alert); escalationScheduler.register(alert) }

    val healthMonitor = HealthMonitor(registeredAdapters, schedulerProvider, 30_000, alertSink)
    healthMonitor.start()
    val statisticsAggregator = StatisticsAggregator(registeredAdapters, schedulerProvider, 60_000)
    statisticsAggregator.start()

    val gatewayInfo = GatewayInfo(version = "7.0.0-platform-jvm", startedAt = Instant.now())

    val apiKey = secretStore.read("admin-api/key") ?: UUID.randomUUID().toString().also { secretStore.write("admin-api/key", it) }
    val authenticator = SecretStoreAdminAuthenticator(secretStore)
    val auditRecorder = EventBasedAdminAuditRecorder(eventPublisher, eventStore, SourceComponent("AdminHttpServer"))

    val adminServer = AdminHttpServer(port = options.adminPort, authenticator = authenticator, auditRecorder = auditRecorder)
    ReadStateEndpoints(registry, managedAdapters, routingRuleAdministration, configurationProvider, statisticsAggregator, diagnosticsFacade).registerRoutes(adminServer)
    AdapterManagementEndpoints(registry, managedAdapters).registerRoutes(adminServer)
    ConfigurationEndpoints(configurationProvider).registerRoutes(adminServer)
    RoutingEndpoints(routingRuleAdministration).registerRoutes(adminServer)
    AlertEndpoints(escalationScheduler).registerRoutes(adminServer)
    MessageEndpoints(messageStore, reprocessingAdministration).registerRoutes(adminServer)
    MonitoringEndpoints(resourceMonitor).registerRoutes(adminServer)
    AdapterConfigurationEndpoints(adapterConfigurationAdministration).registerRoutes(adminServer)
    ConfigurationYamlEndpoints(configurationProvider, yamlCodec, configurationValidator, platformProfile = "jvm").registerRoutes(adminServer)
    RbacEndpoints(accountStore, roleStore, accountAuthenticator).registerRoutes(adminServer)
    DashboardEndpoints(healthMonitor, gatewayInfo, exactlyOnceEngine, eventStore).registerRoutes(adminServer)
    LogEndpoints(eventStore).registerRoutes(adminServer)
    AuditEndpoints(eventStore).registerRoutes(adminServer)
    adminServer.start()

    val uiServer = StaticFileServer(options.uiPort)
    uiServer.start()

    val cliDispatcher = CliDispatcher(
        commands = ReadStateCommands(registry, managedAdapters, routingRuleAdministration, configurationProvider, statisticsAggregator, diagnosticsFacade).commands() +
            AdapterManagementCommands(registry, managedAdapters).commands() +
            ConfigurationCommands(configurationProvider).commands() +
            RoutingCommands(routingRuleAdministration).commands() +
            AlertCommands(escalationScheduler).commands() +
            MessageCommands(messageStore, reprocessingAdministration).commands() +
            MonitoringCommands(resourceMonitor).commands() +
            AdapterConfigurationCommands(adapterConfigurationAdministration).commands() +
            RbacCommands(accountStore, roleStore, accountAuthenticator).commands(),
        auditRecorder = auditRecorder
    )

    return PlatformJvmGateway(
        adminServer = adminServer,
        uiServer = uiServer,
        cliDispatcher = cliDispatcher,
        registry = registry,
        registeredAdapters = registeredAdapters,
        apiKey = apiKey,
        healthMonitor = healthMonitor,
        statisticsAggregator = statisticsAggregator,
        escalationScheduler = escalationScheduler
    )
}

/**
 * Uruchomienie: `./gradlew :platform-jvm:run --args="..."` lub `java -jar platform-jvm.jar`.
 * Argumenty (wszystkie opcjonalne, z wartościami domyślnymi):
 * `--config <ścieżka>` (domyślnie `./config.properties`), `--secrets <ścieżka>` (domyślnie
 * `./secrets.properties`), `--admin-port <port>` (domyślnie 8080), `--ui-port <port>` (domyślnie
 * 8081).
 */
fun main(args: Array<String>) {
    val gateway = buildGateway(parseArgs(args))

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[INFO] Zatrzymywanie :platform-jvm...")
        gateway.stop()
    })

    println("=== midoMAIL :platform-jvm uruchomiony ===")
    println("Admin REST API: http://localhost:${gateway.adminServer.port()} (X-API-Key: ${gateway.apiKey})")
    println("UI: http://localhost:${gateway.uiServer.port()}")
    println("Adaptery zarejestrowane: ${gateway.registeredAdapters.map { it.adapterId.value }}")
    println("Komendy CLI przez stdin (linia = jedna komenda, 'exit' kończy proces):")

    generateSequence(::readLine).takeWhile { it != "exit" }.forEach { line ->
        if (line.isNotBlank()) println(gateway.cliDispatcher.dispatch(line.trim().split(Regex("\\s+")).toTypedArray()))
    }
}

private fun parseArgs(args: Array<String>): ProcessOptions {
    val map = args.toList().chunked(2).associate { it[0] to it.getOrElse(1) { "" } }
    return ProcessOptions(
        configPath = map["--config"] ?: "./config.properties",
        secretsPath = map["--secrets"] ?: "./secrets.properties",
        adminPort = map["--admin-port"]?.toIntOrNull() ?: 8080,
        uiPort = map["--ui-port"]?.toIntOrNull() ?: 8081
    )
}

/**
 * `Registry.register()` rzuca wyjątek po nieudanym `adapter.start()` (ADR-0014) — bez ten
 * uchwycenia, awaria startu JEDNEGO adaptera (np. brak sieci przy uruchomieniu) crashowałaby cały
 * proces `:platform-jvm`, łącznie z adapterami, które poprawnie wystartowały. Ten sam wzorzec co
 * `registerSafely()` w `:platform-android` (GatewayForegroundService.kt).
 */
private fun registerSafely(registry: Registry, adapter: Adapter): Boolean = try {
    registry.register(adapter.adapterId, adapter)
    true
} catch (exception: Exception) {
    println("[ERROR] Rejestracja adaptera ${adapter.adapterId.value} nie powiodła się: ${exception.message}")
    false
}

private fun escalationThresholdFor(level: midomail.domain.health.AlertLevel): Long? = when (level) {
    midomail.domain.health.AlertLevel.CRITICAL -> 5 * 60_000L
    midomail.domain.health.AlertLevel.ERROR -> 30 * 60_000L
    else -> null
}

/** `gateway.fullConfiguration` (ADR-0032) - klucz ustalony, ten sam co `:adapter-rest`. */
private const val FULL_CONFIGURATION_KEY = "gateway.fullConfiguration"

private fun loadStartupDocument(configurationProvider: midomail.domain.port.ConfigurationProvider, yamlCodec: YamlConfigurationCodec): ConfigurationDocument? {
    val yaml = configurationProvider.getValue(FULL_CONFIGURATION_KEY) ?: return null
    return try {
        yamlCodec.decode(yaml)
    } catch (exception: Exception) {
        println("[WARN] Nie udało się wczytać konfiguracji startowej: ${exception.message}")
        null
    }
}

/**
 * Host/port SMTP/IMAP konfigurowalne przez sekrety (domyślnie Gmail, jak `:platform-android`) —
 * umożliwia weryfikację end-to-end (Iteracja 7.8) względem lokalnego serwera testowego (GreenMail,
 * już akceptowana zależność testowa `:adapter-email` od Fazy 2) bez potrzeby prawdziwych
 * poświadczeń Gmail.
 */
private fun createEmailAdapterIfConfigured(secretStore: FileSecretStore, ports: AdapterPorts): Adapter? {
    val username = secretStore.read("email-primary/username") ?: return null
    val password = secretStore.read("email-primary/password") ?: return null
    val factory = EmailAdapterFactory(AdapterId("email-primary"), InMemorySchedulerProvider())
    val configuration = EmailAdapterConfiguration(
        smtp = SmtpConfig(
            host = secretStore.read("email-primary/smtp-host") ?: "smtp.gmail.com",
            port = secretStore.read("email-primary/smtp-port")?.toIntOrNull() ?: 587,
            ssl = secretStore.read("email-primary/smtp-ssl")?.toBooleanStrictOrNull() ?: false,
            starttls = secretStore.read("email-primary/smtp-starttls")?.toBooleanStrictOrNull() ?: true,
            username = username,
            password = password
        ),
        imap = ImapConfig(
            host = secretStore.read("email-primary/imap-host") ?: "imap.gmail.com",
            port = secretStore.read("email-primary/imap-port")?.toIntOrNull() ?: 993,
            imaps = secretStore.read("email-primary/imaps")?.toBooleanStrictOrNull() ?: true,
            starttls = false,
            username = username,
            password = password,
            folder = "INBOX"
        ),
        pollIntervalMillis = secretStore.read("email-primary/poll-interval-millis")?.toLongOrNull() ?: 15_000
    )
    return factory.create(configuration, ports)
}

private fun createWebSocketAdapterIfConfigured(
    configurationProvider: midomail.domain.port.ConfigurationProvider,
    yamlCodec: YamlConfigurationCodec,
    ports: AdapterPorts,
    schedulerProvider: SchedulerProvider
): Adapter? {
    val document = loadStartupDocument(configurationProvider, yamlCodec) ?: return null
    val entry = document.adapters.firstOrNull { it.type == "websocket" && it.enabled } ?: return null
    val url = entry.config["url"] ?: return null
    val configuration = WebSocketAdapterConfiguration(
        url = url,
        reconnectPolicy = ReconnectPolicy(
            maxAttempts = entry.config["reconnect.maxAttempts"]?.toIntOrNull() ?: 5,
            backoffMillis = entry.config["reconnect.backoffMs"]?.toLongOrNull() ?: 2000
        ),
        heartbeatIntervalMillis = entry.config["heartbeatIntervalMs"]?.toLongOrNull() ?: 30_000
    )
    val factory = WebSocketAdapterFactory(AdapterId(entry.adapterId), InMemorySchedulerProvider())
    return factory.create(configuration, ports)
}
