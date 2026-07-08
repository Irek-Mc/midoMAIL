package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.toDto
import midomail.domain.health.AlertId
import midomail.domain.notification.EscalationScheduler

/**
 * Endpointy administracji alertów (SPEC-0025, ADR-0026) — `GET /alerts` (listowanie aktywnych),
 * `POST /alerts/acknowledge?id=` (potwierdzenie, zatrzymuje eskalację).
 */
class AlertEndpoints(private val escalationScheduler: EscalationScheduler) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/alerts") { exchange ->
            val alerts = escalationScheduler.activeAlerts().map { it.toDto() }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(alerts))
        }

        server.route("POST", "/alerts/acknowledge") { exchange ->
            val id = queryParam(exchange, "id")
            if (id == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: id")
                return@route
            }
            escalationScheduler.acknowledge(AlertId(id))
            AdminHttpServer.respond(exchange, 200, "OK")
        }
    }

    private fun queryParam(exchange: com.sun.net.httpserver.HttpExchange, name: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
    }
}
