package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AuditEntryDto
import midomail.domain.event.EventCategory
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.EventStore
import midomail.domain.port.PageRequest

/**
 * Endpoint audytu (SPEC-0025, 70-Uzytkownicy-i-uprawnienia.md §4 „Historia logowania"/„Audyt
 * zmian uprawnień") — `AdminHttpServer` (Faza 5, Iteracja 5.16) już audytuje KAŻDĄ operację
 * zapisu (w tym `POST /accounts`, `/accounts/login`, `/roles`) jako `Event(category=ADMINISTRATIVE)`
 * automatycznie, na poziomie dyspozycji. Ten endpoint wyłącznie odczytuje te wpisy — zero nowej
 * zdolności domenowej, ten sam wzorzec co `LogEndpoints` (Iteracja 6.21) dla kategorii DIAGNOSTIC.
 *
 * Filtrowanie „logowanie" vs „zmiana uprawnień" po stronie klienta (dopasowanie tekstu operacji
 * zawierającego ścieżkę), nie osobne kategorie zdarzeń — jedna kategoria ADMINISTRATIVE dla
 * wszystkich operacji zapisu, zgodnie z SPEC-0024/ADR jej wprowadzającym.
 */
class AuditEndpoints(private val eventStore: EventStore) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/audit") { exchange ->
            val page = eventStore.query(
                EventQueryFilter(category = EventCategory.ADMINISTRATIVE),
                PageRequest(size = queryParam(exchange, "size")?.toIntOrNull() ?: 50)
            )
            val entries = page.items.mapNotNull { event ->
                (event.payload as? String)?.let { operation ->
                    AuditEntryDto(
                        eventId = event.eventId.value,
                        eventType = event.eventType.value,
                        operation = operation,
                        correlationId = event.correlationId.value,
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
