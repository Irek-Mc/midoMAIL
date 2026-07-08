package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.EventDto
import midomail.adapter.rest.dto.ExactlyOnceCountersDto
import midomail.adapter.rest.dto.GatewayStatusDto
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayInfo
import midomail.domain.health.HealthMonitor
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.EventStore
import midomail.domain.port.PageRequest
import java.time.Duration
import java.time.Instant

/**
 * Endpointy Dashboardu (SPEC-0025, ADR-0034, 61-Dashboard.md) — status Gateway, liczniki Exactly
 * Once, ostatnie zdarzenia. „Kolejki" (Waiting/Processing/Retrying/Failed wg Schedulera) świadomie
 * poza zakresem (ADR-0034) — brak endpointu, ekran (Iteracja 6.18) jawnie renderuje niedostępność.
 * Adaptery/Monitoring zasobów już dostępne przez `GET /adapters`/`GET /monitoring/resources`
 * (Faza 5/Iteracja 6.11) — nie duplikowane tutaj.
 */
class DashboardEndpoints(
    private val healthMonitor: HealthMonitor,
    private val gatewayInfo: GatewayInfo,
    private val exactlyOnceEngine: ExactlyOnceEngine,
    private val eventStore: EventStore
) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/dashboard/status") { exchange ->
            val uptimeSeconds = Duration.between(gatewayInfo.startedAt, Instant.now()).seconds
            val dto = GatewayStatusDto(healthMonitor.currentStatus().name, gatewayInfo.version, uptimeSeconds)
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
        }

        server.route("GET", "/dashboard/exactly-once") { exchange ->
            val counters = exactlyOnceEngine.counters()
            AdminHttpServer.respond(exchange, 200, json.encodeToString(ExactlyOnceCountersDto(counters.processed, counters.duplicatesPrevented)))
        }

        server.route("GET", "/dashboard/events") { exchange ->
            val page = eventStore.query(EventQueryFilter(), PageRequest(size = 20))
            val events = page.items.map {
                EventDto(
                    eventId = it.eventId.value,
                    eventType = it.eventType.value,
                    category = it.category.name,
                    timestamp = it.timestamp.toString(),
                    correlationId = it.correlationId.value
                )
            }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(events))
        }
    }
}
