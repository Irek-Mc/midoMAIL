package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AdapterSummaryDto
import midomail.adapter.rest.dto.ConfigEntryDto
import midomail.adapter.rest.dto.RoutingRuleDto
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `ReadStateEndpoints` na prawdziwym `AdminHttpServer` + prawdziwym kliencie HTTP,
 * prawdziwym `Registry` z atrapą `Adapter` (SPEC-0024, §Odczyt stanu).
 */
class ReadStateEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion = "1.0"
        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = setOf(Channel(type = ChannelType("email")))
        override fun supportedCapabilities(): Set<Capability> = setOf(Capability.SUPPORTS_ATTACHMENTS)
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 5, 2, 0)
        override fun send(message: GatewayMessage) {}
    }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to body
    }

    @Test
    fun `GET adapters returns a summary for each registered adapter`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapter = FakeAdapter(AdapterId("email-primary"))
        registry.register(AdapterId("email-primary"), adapter)

        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ReadStateEndpoints(
            registry = registry,
            managedAdapters = ManagedAdapters(listOf(adapter)),
            routingRuleAdministration = RoutingRuleAdministration(),
            configurationProvider = InMemoryConfigurationProvider(),
            statisticsAggregator = StatisticsAggregator(listOf(adapter), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
        ).registerRoutes(server)
        server.start()

        val (statusCode, body) = get("/adapters")

        assertEquals(200, statusCode)
        val summaries = json.decodeFromString<List<AdapterSummaryDto>>(body)
        assertEquals(1, summaries.size)
        assertEquals("email-primary", summaries.single().adapterId)
        assertEquals("1.0", summaries.single().adapterVersion)
        assertEquals("READY", summaries.single().state)
        assertEquals(listOf("email"), summaries.single().channels)
        assertEquals(5L, summaries.single().messagesSent)
    }

    @Test
    fun `GET adapters with an id query parameter filters to a single adapter`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapterA = FakeAdapter(AdapterId("email-primary"))
        val adapterB = FakeAdapter(AdapterId("gsm-primary"))
        registry.register(AdapterId("email-primary"), adapterA)
        registry.register(AdapterId("gsm-primary"), adapterB)

        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ReadStateEndpoints(
            registry = registry,
            managedAdapters = ManagedAdapters(listOf(adapterA, adapterB)),
            routingRuleAdministration = RoutingRuleAdministration(),
            configurationProvider = InMemoryConfigurationProvider(),
            statisticsAggregator = StatisticsAggregator(listOf(adapterA, adapterB), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
        ).registerRoutes(server)
        server.start()

        val (_, body) = get("/adapters?id=gsm-primary")

        val summaries = json.decodeFromString<List<AdapterSummaryDto>>(body)
        assertEquals(1, summaries.size)
        assertEquals("gsm-primary", summaries.single().adapterId)
    }

    @Test
    fun `GET routing_rules returns the current rule set`() {
        val administration = RoutingRuleAdministration(
            listOf(
                RoutingRule(
                    ruleId = RuleId("rule-1"),
                    priority = 100,
                    targetChannel = ChannelType("email"),
                    targetAdapter = AdapterId("email-primary"),
                    deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                    version = RuleVersion("1")
                )
            )
        )
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ReadStateEndpoints(
            registry = Registry(InMemoryEventPublisher()),
            managedAdapters = ManagedAdapters(),
            routingRuleAdministration = administration,
            configurationProvider = InMemoryConfigurationProvider(),
            statisticsAggregator = StatisticsAggregator(emptyList(), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), Registry(InMemoryEventPublisher()))
        ).registerRoutes(server)
        server.start()

        val (statusCode, body) = get("/routing/rules")

        assertEquals(200, statusCode)
        val rules = json.decodeFromString<List<RoutingRuleDto>>(body)
        assertEquals("rule-1", rules.single().ruleId)
    }

    @Test
    fun `GET config with a key query parameter returns the current value and history`() {
        val configurationProvider = InMemoryConfigurationProvider()
        configurationProvider.setValue("gateway.instanceId", "v1")
        configurationProvider.setValue("gateway.instanceId", "v2")

        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ReadStateEndpoints(
            registry = Registry(InMemoryEventPublisher()),
            managedAdapters = ManagedAdapters(),
            routingRuleAdministration = RoutingRuleAdministration(),
            configurationProvider = configurationProvider,
            statisticsAggregator = StatisticsAggregator(emptyList(), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), Registry(InMemoryEventPublisher()))
        ).registerRoutes(server)
        server.start()

        val (statusCode, body) = get("/config?key=gateway.instanceId")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<ConfigEntryDto>(body)
        assertEquals("v2", dto.value)
        assertEquals(listOf("v1"), dto.history)
    }

    @Test
    fun `GET config without a key query parameter returns 400`() {
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ReadStateEndpoints(
            registry = Registry(InMemoryEventPublisher()),
            managedAdapters = ManagedAdapters(),
            routingRuleAdministration = RoutingRuleAdministration(),
            configurationProvider = InMemoryConfigurationProvider(),
            statisticsAggregator = StatisticsAggregator(emptyList(), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), Registry(InMemoryEventPublisher()))
        ).registerRoutes(server)
        server.start()

        val (statusCode, _) = get("/config")

        assertEquals(400, statusCode)
    }

    @Test
    fun `all read-state routes reject requests without valid authentication`() {
        val registry = Registry(InMemoryEventPublisher())
        server = AdminHttpServer(port = 0, authenticator = object : AdminAuthenticator {
            override fun authenticate(providedKey: String): Boolean = false
        })
        ReadStateEndpoints(
            registry = registry,
            managedAdapters = ManagedAdapters(),
            routingRuleAdministration = RoutingRuleAdministration(),
            configurationProvider = InMemoryConfigurationProvider(),
            statisticsAggregator = StatisticsAggregator(emptyList(), InMemorySchedulerProvider(), 60_000),
            diagnosticsFacade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
        ).registerRoutes(server)
        server.start()

        val (statusCode, _) = get("/adapters")

        assertEquals(401, statusCode)
    }
}
