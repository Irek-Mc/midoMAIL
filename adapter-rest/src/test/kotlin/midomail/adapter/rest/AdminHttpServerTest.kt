package midomail.adapter.rest

import midomail.domain.administration.AdminAuthenticator
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza `AdminHttpServer` (ADR-0023) na prawdziwym `HttpServer` + prawdziwym kliencie HTTP
 * (`HttpURLConnection`) — ten sam duch co `WebhookNotificationChannelTest` (Faza 4), nie atrapa.
 */
class AdminHttpServerTest {

    private lateinit var server: AdminHttpServer

    private class FixedKeyAuthenticator(private val expectedKey: String) : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = providedKey == expectedKey
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun startServer(authenticator: AdminAuthenticator = FixedKeyAuthenticator("correct-key")): AdminHttpServer {
        server = AdminHttpServer(port = 0, authenticator = authenticator)
        server.start()
        return server
    }

    private fun get(path: String, apiKey: String? = "correct-key"): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        apiKey?.let { connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, it) }
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to body
    }

    @Test
    fun `a registered route responds with its handler's output when authenticated`() {
        startServer().route("GET", "/ping") { exchange ->
            AdminHttpServer.respond(exchange, 200, "pong")
        }

        val (statusCode, body) = get("/ping")

        assertEquals(200, statusCode)
        assertEquals("pong", body)
    }

    @Test
    fun `a request without a valid API key is rejected with 401 before reaching the handler`() {
        var handlerCalled = false
        startServer().route("GET", "/ping") { exchange ->
            handlerCalled = true
            AdminHttpServer.respond(exchange, 200, "pong")
        }

        val (statusCode, _) = get("/ping", apiKey = "wrong-key")

        assertEquals(401, statusCode)
        assertEquals(false, handlerCalled, "Handler nie powinien być wywołany bez poprawnego uwierzytelnienia")
    }

    @Test
    fun `a request with no API key header at all is rejected with 401`() {
        startServer().route("GET", "/ping") { exchange ->
            AdminHttpServer.respond(exchange, 200, "pong")
        }

        val (statusCode, _) = get("/ping", apiKey = null)

        assertEquals(401, statusCode)
    }

    @Test
    fun `an unregistered route responds with 404`() {
        startServer()

        val (statusCode, _) = get("/unknown")

        assertEquals(404, statusCode)
    }
}
