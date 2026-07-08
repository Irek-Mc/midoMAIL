package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.LogEntryDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.diagnostics.EventStoreSystemLogger
import midomail.domain.diagnostics.LogLevel
import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.memory.InMemoryEventStore
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza `LogEndpoints` (SPEC-0025, 69-Logi.md) na prawdziwym serwerze/kliencie i prawdziwym
 * `EventStoreSystemLogger`.
 */
class LogEndpointsTest {

    private lateinit var server: AdminHttpServer

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(): Pair<InMemoryEventStore, EventStoreSystemLogger> {
        val eventStore = InMemoryEventStore()
        val logger = EventStoreSystemLogger(eventStore, SourceComponent("GatewayEngine"))
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        LogEndpoints(eventStore).registerRoutes(server)
        server.start()
        return eventStore to logger
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
    fun `GET logs returns entries logged through EventStoreSystemLogger`() {
        val (_, logger) = setup()
        logger.log(LogLevel.INFO, "Gateway wystartował", null, null)

        val (statusCode, body) = get("/logs")

        assertEquals(200, statusCode)
        val entries = Json.decodeFromString<List<LogEntryDto>>(body)
        assertEquals("Gateway wystartował", entries.single().message)
        assertEquals("INFO", entries.single().level)
    }

    @Test
    fun `GET logs filters by level`() {
        val (_, logger) = setup()
        logger.log(LogLevel.INFO, "info message", null, null)
        logger.log(LogLevel.ERROR, "error message", null, null)

        val (_, body) = get("/logs?level=ERROR")

        val entries = Json.decodeFromString<List<LogEntryDto>>(body)
        assertEquals(listOf("error message"), entries.map { it.message })
    }

    @Test
    fun `GET logs filters by correlationId`() {
        val (_, logger) = setup()
        logger.log(LogLevel.INFO, "message A", CorrelationId("correlation-a"), null)
        logger.log(LogLevel.INFO, "message B", CorrelationId("correlation-b"), null)

        val (_, body) = get("/logs?correlationId=correlation-a")

        val entries = Json.decodeFromString<List<LogEntryDto>>(body)
        assertEquals(listOf("message A"), entries.map { it.message })
    }

    @Test
    fun `GET logs excludes non-DIAGNOSTIC events even if present in the same EventStore`() {
        val (eventStore, logger) = setup()
        logger.log(LogLevel.INFO, "a log entry", null, null)
        eventStore.record(
            Event(
                eventId = EventId("event-1"),
                eventType = EventType("domain.message_routed"),
                eventVersion = EventVersion("1.0"),
                category = EventCategory.DOMAIN,
                timestamp = java.time.Instant.now(),
                correlationId = CorrelationId("correlation-1"),
                sourceComponent = SourceComponent("GatewayEngine"),
                payload = "not a LogEntry"
            )
        )

        val (_, body) = get("/logs")

        val entries = Json.decodeFromString<List<LogEntryDto>>(body)
        assertEquals(1, entries.size)
    }
}
