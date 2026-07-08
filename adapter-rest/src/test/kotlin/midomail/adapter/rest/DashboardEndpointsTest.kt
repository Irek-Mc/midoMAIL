package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.EventDto
import midomail.adapter.rest.dto.ExactlyOnceCountersDto
import midomail.adapter.rest.dto.GatewayStatusDto
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayInfo
import midomail.domain.health.HealthMonitor
import midomail.domain.message.Channel
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `DashboardEndpoints` (SPEC-0025, ADR-0034) na prawdziwym serwerze/kliencie.
 */
class DashboardEndpointsTest {

    private lateinit var server: AdminHttpServer
    private lateinit var healthMonitor: HealthMonitor
    private val json = Json { ignoreUnknownKeys = true }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

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

    @AfterTest
    fun stopAll() {
        server.stop()
        healthMonitor.stop()
    }

    private fun setup(): Triple<ExactlyOnceEngine, InMemoryEventStore, GatewayInfo> {
        val exactlyOnceEngine = ExactlyOnceEngine(InMemoryMessageStore())
        val eventStore = InMemoryEventStore()
        val gatewayInfo = GatewayInfo(version = "2.0.0-test", startedAt = Instant.now().minusSeconds(3600))
        healthMonitor = HealthMonitor(
            adapters = listOf(FakeAdapter(AdapterId("email-primary"))),
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 60_000,
            alertSink = midomail.domain.health.AlertSink { }
        )
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        DashboardEndpoints(healthMonitor, gatewayInfo, exactlyOnceEngine, eventStore).registerRoutes(server)
        server.start()
        return Triple(exactlyOnceEngine, eventStore, gatewayInfo)
    }

    private fun get(path: String): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        val responseCode = connection.responseCode
        val body = connection.inputStream.readBytes().toString(Charsets.UTF_8)
        return responseCode to body
    }

    @Test
    fun `GET dashboard_status reports READY, version and a positive uptime`() {
        setup()

        val (statusCode, body) = get("/dashboard/status")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<GatewayStatusDto>(body)
        assertEquals("READY", dto.status)
        assertEquals("2.0.0-test", dto.version)
        assertTrue(dto.uptimeSeconds >= 3600)
    }

    @Test
    fun `GET dashboard_exactly-once reports the current counters`() {
        val (exactlyOnceEngine, _, _) = setup()
        exactlyOnceEngine.processIfNew(ExternalReference("<ext-1@localhost>"), sampleMessage())

        val (statusCode, body) = get("/dashboard/exactly-once")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<ExactlyOnceCountersDto>(body)
        assertEquals(1, dto.processed)
        assertEquals(0, dto.duplicatesPrevented)
    }

    @Test
    fun `GET dashboard_events returns recorded events, most recent first`() {
        val (_, eventStore, _) = setup()
        eventStore.record(sampleEvent("event-1", Instant.now().minusSeconds(10)))
        eventStore.record(sampleEvent("event-2", Instant.now()))

        val (statusCode, body) = get("/dashboard/events")

        assertEquals(200, statusCode)
        val events = json.decodeFromString<List<EventDto>>(body)
        assertEquals(listOf("event-2", "event-1"), events.map { it.eventId })
    }

    private fun sampleMessage(): GatewayMessage = GatewayMessage(
        identity = midomail.domain.message.Identity(
            messageId = midomail.domain.message.MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = midomail.domain.message.SchemaVersion("2.0"),
            externalReference = ExternalReference("<ext-1@localhost>")
        ),
        source = Channel(type = midomail.domain.message.ChannelType("gsm")),
        destination = Channel(type = midomail.domain.message.ChannelType("email")),
        payload = midomail.domain.message.Payload(content = "test")
    )

    private fun sampleEvent(eventId: String, timestamp: Instant): Event = Event(
        eventId = EventId(eventId),
        eventType = EventType("domain.message_routed"),
        eventVersion = EventVersion("1.0"),
        category = EventCategory.DOMAIN,
        timestamp = timestamp,
        correlationId = CorrelationId("correlation-1"),
        sourceComponent = SourceComponent("GatewayEngine"),
        payload = "test"
    )
}
