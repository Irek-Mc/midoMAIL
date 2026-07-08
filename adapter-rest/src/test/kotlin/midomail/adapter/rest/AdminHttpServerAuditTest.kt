package midomail.adapter.rest

import midomail.domain.administration.AdminAuditRecorder
import midomail.domain.administration.AdminAuthenticator
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza integrację audytu w `AdminHttpServer` (Iteracja 5.16, SPEC-0024 §Uwierzytelnianie
 * i audyt).
 */
class AdminHttpServerAuditTest {

    private lateinit var server: AdminHttpServer

    private class FixedKeyAuthenticator(private val expectedKey: String) : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = providedKey == expectedKey
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun get(path: String, apiKey: String?, method: String = "GET"): Int {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        apiKey?.let { connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, it) }
        if (method != "GET") {
            connection.doOutput = true
            connection.outputStream.close()
        }
        return connection.responseCode
    }

    @Test
    fun `an authentication failure is recorded regardless of HTTP method`() {
        val recorded = CopyOnWriteArrayList<Pair<String, Boolean>>()
        server = AdminHttpServer(port = 0, authenticator = FixedKeyAuthenticator("correct-key"), auditRecorder = AdminAuditRecorder { op, authenticated -> recorded.add(op to authenticated) })
        server.route("GET", "/ping") { exchange -> AdminHttpServer.respond(exchange, 200, "pong") }
        server.start()

        get("/ping", apiKey = "wrong-key")

        assertEquals(1, recorded.size)
        assertEquals(false, recorded.single().second)
    }

    @Test
    fun `a successful write operation is recorded`() {
        val recorded = CopyOnWriteArrayList<Pair<String, Boolean>>()
        server = AdminHttpServer(port = 0, authenticator = FixedKeyAuthenticator("correct-key"), auditRecorder = AdminAuditRecorder { op, authenticated -> recorded.add(op to authenticated) })
        server.route("POST", "/adapters/disable") { exchange -> AdminHttpServer.respond(exchange, 200, "OK") }
        server.start()

        get("/adapters/disable", apiKey = "correct-key", method = "POST")

        assertEquals(1, recorded.size)
        assertTrue(recorded.single().second)
        assertTrue(recorded.single().first.contains("POST"))
    }

    @Test
    fun `a successful GET (read) is not audited`() {
        val recorded = CopyOnWriteArrayList<Pair<String, Boolean>>()
        server = AdminHttpServer(port = 0, authenticator = FixedKeyAuthenticator("correct-key"), auditRecorder = AdminAuditRecorder { op, authenticated -> recorded.add(op to authenticated) })
        server.route("GET", "/ping") { exchange -> AdminHttpServer.respond(exchange, 200, "pong") }
        server.start()

        get("/ping", apiKey = "correct-key")

        assertEquals(0, recorded.size)
    }
}
