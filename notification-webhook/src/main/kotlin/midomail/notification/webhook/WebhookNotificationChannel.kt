package midomail.notification.webhook

import midomail.domain.health.Alert
import midomail.domain.notification.NotificationChannel
import midomail.domain.notification.NotificationResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kanał powiadomień Webhook (ADR-0017-Webhook-Klient-HTTP.md) — generyczne HTTP POST z
 * ustrukturyzowanym ładunkiem JSON (38-Powiadomienia.md §3: poziom, źródło, czas, treść, status).
 * Jedyny mechanizm integracji z PagerDuty/OpsGenie/Slack i podobnymi (ADR-0007) — bez dedykowanych
 * klientów.
 *
 * `HttpURLConnection`, nie `java.net.http.HttpClient` — dostępny od Android API 1 (ADR-0017,
 * urządzenie testowe Fazy 3 to API 28, poniżej wymaganego API 34 dla `HttpClient`).
 *
 * Retry wyłącznie na 5xx/błąd IO (34-Error-Handling.md §5/§6: „niepowodzenie... jest obsługiwane
 * zgodnie z ogólną polityką Retry") — 4xx jest błędem klienta (zła konfiguracja URL/ładunku),
 * ponowienie tej samej treści nie pomoże.
 */
class WebhookNotificationChannel(
    private val url: String,
    private val maxAttempts: Int = 3
) : NotificationChannel {

    override fun deliver(alert: Alert): NotificationResult {
        var lastFailureReason = "Brak prób wysyłki"
        repeat(maxAttempts) {
            try {
                val responseCode = post(buildJson(alert))
                if (responseCode in 200..299) return NotificationResult.Delivered
                lastFailureReason = "HTTP $responseCode"
                if (responseCode < 500) return NotificationResult.Failed(lastFailureReason)
            } catch (exception: IOException) {
                lastFailureReason = exception.message ?: "Błąd połączenia"
            }
        }
        return NotificationResult.Failed(lastFailureReason)
    }

    private fun post(jsonBody: String): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun buildJson(alert: Alert): String {
        val fields = listOf(
            "level" to alert.level.name,
            "source" to alert.source.value,
            "timestamp" to alert.timestamp.toString(),
            "status" to alert.status.name,
            "content" to (alert.recommendedAction ?: "")
        )
        return fields.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
            "\"$key\":\"${escapeJson(value)}\""
        }
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
