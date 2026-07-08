package midomail.ui.web

import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `StaticFileServer` (ADR-0033) na prawdziwym serwerze i prawdziwym kliencie HTTP.
 */
class StaticFileServerTest {

    private lateinit var server: StaticFileServer

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun get(path: String): Pair<Int, HttpURLConnection> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        val responseCode = connection.responseCode
        return responseCode to connection
    }

    private fun getBody(path: String): String {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return connection.inputStream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `GET slash serves index html`() {
        server = StaticFileServer(port = 0)
        server.start()

        val (statusCode, _) = get("/")

        assertEquals(200, statusCode)
        assertTrue(getBody("/").contains("midoMAIL Gateway"))
    }

    @Test
    fun `GET a known static resource returns the correct content type`() {
        server = StaticFileServer(port = 0)
        server.start()

        val (statusCode, connection) = get("/index.html")

        assertEquals(200, statusCode)
        assertTrue(connection.getHeaderField("Content-Type")?.contains("text/html") == true)
    }

    @Test
    fun `GET an unknown path returns 404`() {
        server = StaticFileServer(port = 0)
        server.start()

        val (statusCode, _) = get("/does-not-exist.html")

        assertEquals(404, statusCode)
    }

    @Test
    fun `GET the app stylesheet returns 200 with a CSS content type`() {
        server = StaticFileServer(port = 0)
        server.start()

        val (statusCode, connection) = get("/css/app.css")

        assertEquals(200, statusCode)
        assertTrue(connection.getHeaderField("Content-Type")?.contains("text/css") == true)
    }

    @Test
    fun `GET each of the shell's JS files returns 200 with a JS content type`() {
        server = StaticFileServer(port = 0)
        server.start()

        listOf(
            "/js/api.js", "/js/router.js", "/js/app.js",
            "/js/views/dashboard.js", "/js/views/monitoring.js", "/js/views/diagnostics.js", "/js/views/logs.js",
            "/js/views/statistics.js", "/js/views/messages.js", "/js/views/routing.js", "/js/views/adapters.js",
            "/js/views/configuration.js", "/js/views/users.js"
        ).forEach { path ->
            val (statusCode, connection) = get(path)
            assertEquals(200, statusCode, "oczekiwano 200 dla $path")
            assertTrue(connection.getHeaderField("Content-Type")?.contains("javascript") == true, "oczekiwano Content-Type javascript dla $path")
        }
    }

    @Test
    fun `index html references the navigation areas from 60-UX-Filozofia md paragraf 3`() {
        server = StaticFileServer(port = 0)
        server.start()

        val body = getBody("/")

        listOf("dashboard", "messages", "routing", "adapters", "configuration", "monitoring", "diagnostics", "statistics", "logs", "users")
            .forEach { area -> assertTrue(body.contains("data-area=\"$area\""), "brak obszaru nawigacji: $area") }
    }

    @Test
    fun `app css defines a mobile media query for widoki mobilne`() {
        server = StaticFileServer(port = 0)
        server.start()

        val css = getBody("/css/app.css")

        assertTrue(css.contains("@media"), "brak media query dla widoków mobilnych (Iteracja 6.28)")
        assertTrue(css.contains(".mobile-hidden"), "brak reguły ukrywającej pozycje zaawansowane na mobile")
    }

    @Test
    fun `index html marks Routing and Konfiguracja as mobile-hidden, per 71-Widoki-mobilne §4`() {
        server = StaticFileServer(port = 0)
        server.start()

        val body = getBody("/")

        assertTrue(body.contains("class=\"mobile-hidden\"><a href=\"#/routing\""))
        assertTrue(body.contains("class=\"mobile-hidden\"><a href=\"#/configuration\""))
    }
}
