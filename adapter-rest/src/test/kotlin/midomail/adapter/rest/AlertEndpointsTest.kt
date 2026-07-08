package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AlertDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.notification.EscalationScheduler
import midomail.domain.notification.NotificationChannel
import midomail.domain.notification.NotificationResult
import midomail.domain.notification.NotificationRouter
import midomail.domain.port.memory.InMemorySchedulerProvider
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza `AlertEndpoints` (SPEC-0025, ADR-0026) na prawdziwym serwerze/kliencie.
 */
class AlertEndpointsTest {

    private lateinit var server: AdminHttpServer
    private lateinit var scheduler: EscalationScheduler
    private val json = Json { ignoreUnknownKeys = true }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    private class NoopChannel : NotificationChannel {
        override fun deliver(alert: Alert): NotificationResult = NotificationResult.Delivered
    }

    @AfterTest
    fun stopAll() {
        server.stop()
        scheduler.stop()
    }

    private fun setup(): EscalationScheduler {
        scheduler = EscalationScheduler(
            schedulerProvider = InMemorySchedulerProvider(),
            checkIntervalMillis = 60_000,
            escalateAfterMillis = { 60_000L },
            router = NotificationRouter(mapOf(AlertLevel.CRITICAL to listOf(NoopChannel())))
        )
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        AlertEndpoints(scheduler).registerRoutes(server)
        server.start()
        return scheduler
    }

    private fun get(path: String, method: String = "GET"): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        if (method != "GET") {
            connection.doOutput = true
            connection.outputStream.close()
        }
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to body
    }

    private fun alert(id: String = "alert-1"): Alert = Alert(
        alertId = AlertId(id),
        level = AlertLevel.CRITICAL,
        source = SourceComponent("gsm-primary"),
        timestamp = Instant.now(),
        status = AlertStatus.ACTIVE
    )

    @Test
    fun `GET alerts returns every active alert`() {
        val scheduler = setup()
        scheduler.register(alert("alert-1"))
        scheduler.register(alert("alert-2"))

        val (statusCode, body) = get("/alerts")

        assertEquals(200, statusCode)
        val alerts = json.decodeFromString<List<AlertDto>>(body)
        assertEquals(setOf("alert-1", "alert-2"), alerts.map { it.alertId }.toSet())
    }

    @Test
    fun `POST alerts_acknowledge removes the alert from the active list`() {
        val scheduler = setup()
        scheduler.register(alert("alert-1"))

        val (statusCode, _) = get("/alerts/acknowledge?id=alert-1", method = "POST")

        assertEquals(200, statusCode)
        assertEquals(0, scheduler.activeAlerts().size)
    }

    @Test
    fun `POST alerts_acknowledge without an id query parameter returns 400`() {
        setup()

        val (statusCode, _) = get("/alerts/acknowledge", method = "POST")

        assertEquals(400, statusCode)
    }
}
