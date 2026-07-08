package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.LogEntryDto
import midomail.domain.diagnostics.LogEntry
import midomail.domain.event.EventCategory
import midomail.domain.event.EventType
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.EventStore
import midomail.domain.port.PageRequest
import java.time.Instant

/**
 * Endpoint Logów (SPEC-0025, 69-Logi.md) — `EventStoreSystemLogger` (Faza 4) zapisuje logi jako
 * `Event(category = DIAGNOSTIC, payload = LogEntry)` w `EventStore` (SPEC-0023); ten endpoint
 * filtruje po tej kategorii i bezpiecznie rzutuje payload (wyłącznie `EventStoreSystemLogger`
 * zapisuje zdarzenia tej kategorii).
 *
 * **Filtrowanie po MessageId/ExternalReference niedostępne** (69-Logi.md §3) — `Event` niesie
 * wyłącznie `CorrelationId`, nie `MessageId`/`ExternalReference`. Zamierzony przepływ: znajdź
 * komunikat w Diagnostyce (Iteracja 6.20), skopiuj jego `CorrelationId` (§4: „Kopiowanie
 * CorrelationId"), przefiltruj Logi po nim — nie luka, tylko pośredni, spójny z resztą modelu
 * przepływ.
 *
 * **„Strumień na żywo" (SA-11) — polling po stronie klienta**, nie SSE/WebSocket — świadome
 * uproszczenie (`com.sun.net.httpserver.HttpServer` bez natywnego wsparcia), udokumentowane w
 * SPEC-0025.
 */
class LogEndpoints(private val eventStore: EventStore) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/logs") { exchange ->
            val filter = EventQueryFilter(
                category = EventCategory.DIAGNOSTIC,
                correlationId = queryParam(exchange, "correlationId")?.let { CorrelationId(it) },
                eventType = queryParam(exchange, "level")?.let { EventType("log.${it.lowercase()}") },
                sourceComponent = queryParam(exchange, "component")?.let { SourceComponent(it) },
                createdAfter = queryParam(exchange, "createdAfter")?.let { Instant.parse(it) },
                createdBefore = queryParam(exchange, "createdBefore")?.let { Instant.parse(it) }
            )
            val page = eventStore.query(
                filter,
                PageRequest(cursor = queryParam(exchange, "cursor"), size = queryParam(exchange, "size")?.toIntOrNull() ?: 50)
            )
            val entries = page.items.mapNotNull { event ->
                (event.payload as? LogEntry)?.let { logEntry ->
                    LogEntryDto(
                        eventId = event.eventId.value,
                        level = logEntry.level.name,
                        message = logEntry.message,
                        correlationId = event.correlationId.value,
                        sourceComponent = event.sourceComponent.value,
                        timestamp = event.timestamp.toString()
                    )
                }
            }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(entries))
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
