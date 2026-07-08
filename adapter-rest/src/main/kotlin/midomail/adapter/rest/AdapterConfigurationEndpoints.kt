package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AdapterConfigurationDto
import midomail.domain.adapter.AdapterId
import midomail.domain.administration.AdapterConfigurationAdministration

/**
 * Endpointy typowanej konfiguracji adaptera (SPEC-0025, ADR-0031, 64-Adaptery.md §6) — brak
 * wsparcia dla parametrów ścieżkowych w `AdminHttpServer`, więc `adapterId`/`type` są parametrami
 * zapytania.
 */
class AdapterConfigurationEndpoints(private val administration: AdapterConfigurationAdministration) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/adapters/configuration") { exchange ->
            val id = queryParam(exchange, "id")
            val type = queryParam(exchange, "type")
            if (id == null || type == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagane parametry zapytania: id, type")
                return@route
            }
            val fields = administration.read(AdapterId(id), type)
            AdminHttpServer.respond(exchange, 200, json.encodeToString(AdapterConfigurationDto(fields)))
        }

        server.route("POST", "/adapters/configuration") { exchange ->
            val id = queryParam(exchange, "id")
            val type = queryParam(exchange, "type")
            val field = queryParam(exchange, "field")
            if (id == null || type == null || field == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagane parametry zapytania: id, type, field")
                return@route
            }
            val value = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            try {
                administration.write(AdapterId(id), type, field, value)
                AdminHttpServer.respond(exchange, 200, "OK")
            } catch (exception: IllegalArgumentException) {
                AdminHttpServer.respond(exchange, 400, exception.message ?: "Nieprawidłowe pole")
            }
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
