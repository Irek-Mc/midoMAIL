package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.toDto
import midomail.domain.monitoring.ResourceMonitor

/** Endpoint monitoringu zasobów (SPEC-0025, ADR-0027, 66-Monitoring.md §2). */
class MonitoringEndpoints(private val resourceMonitor: ResourceMonitor) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/monitoring/resources") { exchange ->
            AdminHttpServer.respond(exchange, 200, json.encodeToString(resourceMonitor.snapshot().toDto()))
        }
    }
}
