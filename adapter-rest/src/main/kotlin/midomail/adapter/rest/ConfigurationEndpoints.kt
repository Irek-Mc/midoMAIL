package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.ConfigEntryDto
import midomail.domain.port.ConfigurationProvider

/**
 * Endpointy zapisu konfiguracji (SPEC-0024-Administrative-API-Contract.md, §3 Konfiguracja) —
 * `GET /config?key=` (odczyt + historia) już zbudowany w `ReadStateEndpoints` (Iteracja 5.10);
 * tu wyłącznie operacje zapisu, na głębokości ustalonej w ADR-0020 (wąski zapis + historia w
 * pamięci, bez pełnego parsera YAML/importu-eksportu/walidacji krzyżowej).
 */
class ConfigurationEndpoints(private val configurationProvider: ConfigurationProvider) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("POST", "/config") { exchange ->
            val key = queryParam(exchange, "key")
            if (key == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: key")
                return@route
            }
            val value = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            configurationProvider.setValue(key, value)
            val dto = ConfigEntryDto(key = key, value = value, history = configurationProvider.history(key))
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
        }

        server.route("POST", "/config/rollback") { exchange ->
            val key = queryParam(exchange, "key")
            if (key == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: key")
                return@route
            }
            val restored = configurationProvider.rollback(key)
            if (restored == null) {
                AdminHttpServer.respond(exchange, 409, "Brak historii do przywrócenia dla klucza: $key")
                return@route
            }
            val dto = ConfigEntryDto(key = key, value = restored, history = configurationProvider.history(key))
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
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
