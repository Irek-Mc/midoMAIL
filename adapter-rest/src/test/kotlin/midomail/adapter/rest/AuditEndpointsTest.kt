package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AuditEntryDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.administration.EventBasedAdminAuditRecorder
import midomail.domain.event.SourceComponent
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryEventPublisher
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `AuditEndpoints` (SPEC-0025, 70-Uzytkownicy-i-uprawnienia.md §4) na prawdziwym
 * serwerze/kliencie, z prawdziwym generycznym mechanizmem audytu `AdminHttpServer`
 * (Iteracja 5.16) — nie atrapą.
 */
class AuditEndpointsTest {

    private lateinit var server: AdminHttpServer

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun request(method: String, path: String): Int {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        if (method != "GET") {
            connection.doOutput = true
            connection.outputStream.close()
        }
        return connection.responseCode
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
    fun `GET audit returns entries recorded automatically by write operations on other routes`() {
        val eventStore = InMemoryEventStore()
        val auditRecorder = EventBasedAdminAuditRecorder(InMemoryEventPublisher(), eventStore, SourceComponent("AdminHttpServer"))
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator(), auditRecorder = auditRecorder)
        server.route("POST", "/accounts") { exchange -> AdminHttpServer.respond(exchange, 200, "OK") }
        AuditEndpoints(eventStore).registerRoutes(server)
        server.start()

        request("POST", "/accounts")

        val (statusCode, body) = get("/audit")

        assertEquals(200, statusCode)
        val entries = Json.decodeFromString<List<AuditEntryDto>>(body)
        assertTrue(entries.any { it.operation.contains("/accounts") })
    }

    @Test
    fun `GET audit includes authentication failures`() {
        val eventStore = InMemoryEventStore()
        val auditRecorder = EventBasedAdminAuditRecorder(InMemoryEventPublisher(), eventStore, SourceComponent("AdminHttpServer"))
        server = AdminHttpServer(port = 0, authenticator = object : AdminAuthenticator {
            override fun authenticate(providedKey: String): Boolean = providedKey == "correct-key"
        }, auditRecorder = auditRecorder)
        server.route("GET", "/ping") { exchange -> AdminHttpServer.respond(exchange, 200, "pong") }
        AuditEndpoints(eventStore).registerRoutes(server)
        server.start()

        val wrongKeyConnection = URL("http://localhost:${server.port()}/ping").openConnection() as HttpURLConnection
        wrongKeyConnection.requestMethod = "GET"
        wrongKeyConnection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "wrong-key")
        wrongKeyConnection.responseCode

        val auditConnection = URL("http://localhost:${server.port()}/audit").openConnection() as HttpURLConnection
        auditConnection.requestMethod = "GET"
        auditConnection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "correct-key")
        val body = auditConnection.inputStream.readBytes().toString(Charsets.UTF_8)

        val entries = Json.decodeFromString<List<AuditEntryDto>>(body)
        assertTrue(entries.any { it.eventType.contains("auth_failure") })
    }
}
