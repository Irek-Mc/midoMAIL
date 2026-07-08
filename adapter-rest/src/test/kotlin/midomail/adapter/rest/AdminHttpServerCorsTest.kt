package midomail.adapter.rest

import midomail.domain.administration.AdminAuthenticator
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza CORS w `AdminHttpServer` (Iteracja 6.16, ADR-0033, SA-13).
 */
class AdminHttpServerCorsTest {

    private lateinit var server: AdminHttpServer

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    private class DenyAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = false
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `OPTIONS preflight returns 204 with CORS headers, without requiring authentication`() {
        server = AdminHttpServer(port = 0, authenticator = DenyAllAuthenticator())
        server.route("GET", "/ping") { exchange -> AdminHttpServer.respond(exchange, 200, "pong") }
        server.start()

        val connection = URL("http://localhost:${server.port()}/ping").openConnection() as HttpURLConnection
        connection.requestMethod = "OPTIONS"

        assertEquals(204, connection.responseCode)
        assertEquals("*", connection.getHeaderField("Access-Control-Allow-Origin"))
        assertEquals(true, connection.getHeaderField("Access-Control-Allow-Methods")?.contains("POST"))
    }

    @Test
    fun `a successful GET response includes the CORS header`() {
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        server.route("GET", "/ping") { exchange -> AdminHttpServer.respond(exchange, 200, "pong") }
        server.start()

        val connection = URL("http://localhost:${server.port()}/ping").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        connection.responseCode

        assertEquals("*", connection.getHeaderField("Access-Control-Allow-Origin"))
    }

    @Test
    fun `a 401 error response also includes the CORS header`() {
        server = AdminHttpServer(port = 0, authenticator = DenyAllAuthenticator())
        server.route("GET", "/ping") { exchange -> AdminHttpServer.respond(exchange, 200, "pong") }
        server.start()

        val connection = URL("http://localhost:${server.port()}/ping").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        assertEquals(401, connection.responseCode)
        assertEquals("*", connection.getHeaderField("Access-Control-Allow-Origin"))
    }
}
