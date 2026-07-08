package midomail.notification.webhook

import com.sun.net.httpserver.HttpServer
import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.notification.NotificationResult
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Weryfikuje `WebhookNotificationChannel` na prawdziwym, lokalnym `HttpServer` (wbudowanym w JDK,
 * nie atrapie — 50-Quality/50-Testy.md §5, ten sam duch co GreenMail w `:adapter-email`).
 */
class WebhookNotificationChannelTest {

    private lateinit var server: HttpServer
    private val receivedBodies = mutableListOf<String>()
    private val receivedContentTypes = mutableListOf<String?>()

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    private fun startServer(responseCode: Int) {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/webhook") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            receivedBodies.add(body)
            receivedContentTypes.add(exchange.requestHeaders.getFirst("Content-Type"))
            exchange.sendResponseHeaders(responseCode, -1)
            exchange.close()
        }
        server.start()
    }

    private fun url(): String = "http://localhost:${server.address.port}/webhook"

    private fun createAlert(): Alert = Alert(
        alertId = AlertId("alert-1"),
        level = AlertLevel.CRITICAL,
        source = SourceComponent("gsm-primary"),
        timestamp = Instant.parse("2026-07-06T12:00:00Z"),
        status = AlertStatus.ACTIVE,
        recommendedAction = "Sprawdź kartę SIM"
    )

    @Test
    fun `a successful POST returns Delivered and carries the expected JSON fields`() {
        startServer(responseCode = 200)
        val channel = WebhookNotificationChannel(url())

        val result = channel.deliver(createAlert())

        assertEquals(NotificationResult.Delivered, result)
        assertEquals(1, receivedBodies.size)
        val body = receivedBodies.single()
        assertTrue(body.contains("\"level\":\"CRITICAL\""))
        assertTrue(body.contains("\"source\":\"gsm-primary\""))
        assertTrue(body.contains("\"status\":\"ACTIVE\""))
        assertTrue(body.contains("\"content\":\"Sprawdź kartę SIM\""))
        assertTrue(receivedContentTypes.single()?.startsWith("application/json") == true)
    }

    @Test
    fun `a 4xx response is returned as Failed without retrying`() {
        startServer(responseCode = 400)
        val channel = WebhookNotificationChannel(url(), maxAttempts = 3)

        val result = channel.deliver(createAlert())

        assertIs<NotificationResult.Failed>(result)
        assertEquals(1, receivedBodies.size, "4xx nie powinien być ponawiany")
    }

    @Test
    fun `a 500 response is retried and a subsequent success is reported as Delivered`() {
        val attempt = AtomicInteger(0)
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/webhook") { exchange ->
            val code = if (attempt.getAndIncrement() == 0) 500 else 200
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(code, -1)
            exchange.close()
        }
        server.start()
        val channel = WebhookNotificationChannel(url(), maxAttempts = 3)

        val result = channel.deliver(createAlert())

        assertEquals(NotificationResult.Delivered, result)
        assertEquals(2, attempt.get(), "Powinna nastąpić dokładnie jedna próba ponowienia po 500")
    }

    @Test
    fun `persistent 5xx failures exhaust retries and are reported as Failed`() {
        startServer(responseCode = 503)
        val channel = WebhookNotificationChannel(url(), maxAttempts = 3)

        val result = channel.deliver(createAlert())

        assertIs<NotificationResult.Failed>(result)
        assertEquals(3, receivedBodies.size, "Powinny nastąpić dokładnie maxAttempts prób")
    }
}
