package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AdapterSummaryDto
import midomail.adapter.rest.dto.ConfigEntryDto
import midomail.adapter.rest.dto.EventDto
import midomail.adapter.rest.dto.MessageTraceDto
import midomail.adapter.rest.dto.MetricsSnapshotDto
import midomail.adapter.rest.dto.toDto
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.message.CorrelationId
import midomail.domain.port.ConfigurationProvider
import midomail.domain.statistics.StatisticsAggregator

/**
 * Endpointy odczytu stanu (SPEC-0024-Administrative-API-Contract.md, §1 Odczyt stanu) — mapuje
 * porty administracyjne (Część A Fazy 5) na DTO (ADR-0024) i rejestruje trasy GET na
 * [AdminHttpServer].
 *
 * [managedAdapters] to współdzielony, mutowalny widok żywych instancji `Adapter` (nie tylko
 * `AdapterId` z `Registry`) — `Registry` sam nie przechowuje `supportedChannels()`/
 * `supportedCapabilities()`/`health()`/`metrics()` (ADR-0018, granica nienaruszona). Współdzielony
 * z endpointami zarządzania adapterami (Iteracja 5.11), żeby operacja usuń/restart była
 * natychmiast widoczna w odczycie.
 */
class ReadStateEndpoints(
    private val registry: Registry,
    private val managedAdapters: ManagedAdapters,
    private val routingRuleAdministration: RoutingRuleAdministration,
    private val configurationProvider: ConfigurationProvider,
    private val statisticsAggregator: StatisticsAggregator,
    private val diagnosticsFacade: DiagnosticsFacade
) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/adapters") { exchange ->
            val requestedId = queryParam(exchange, "id")
            val summaries = managedAdapters.all()
                .filter { requestedId == null || it.adapterId.value == requestedId }
                .map { toSummaryDto(it) }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(summaries))
        }

        server.route("GET", "/routing/rules") { exchange ->
            val rules = routingRuleAdministration.list().map { it.toDto() }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(rules))
        }

        server.route("GET", "/config") { exchange ->
            val key = queryParam(exchange, "key")
            if (key == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: key")
                return@route
            }
            val dto = ConfigEntryDto(
                key = key,
                value = configurationProvider.getValue(key),
                history = configurationProvider.history(key)
            )
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
        }

        server.route("GET", "/statistics") { exchange ->
            val requestedId = queryParam(exchange, "adapterId")
            val snapshots = statisticsAggregator.snapshots()
                .filter { requestedId == null || it.adapterId.value == requestedId }
                .map { snapshot ->
                    MetricsSnapshotDto(
                        adapterId = snapshot.adapterId.value,
                        periodStart = snapshot.periodStart.toString(),
                        periodEnd = snapshot.periodEnd.toString(),
                        messagesSent = snapshot.messagesSent,
                        messagesReceived = snapshot.messagesReceived,
                        errorCount = snapshot.errorCount,
                        throttledCount = snapshot.throttledCount
                    )
                }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(snapshots))
        }

        server.route("GET", "/diagnostics/trace") { exchange ->
            val correlationId = queryParam(exchange, "correlationId")
            if (correlationId == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: correlationId")
                return@route
            }
            val trace = diagnosticsFacade.messageTrace(CorrelationId(correlationId))
            val dto = MessageTraceDto(
                correlationId = correlationId,
                messageIds = trace.messages.map { it.identity.messageId.value },
                events = trace.events.map {
                    EventDto(
                        eventId = it.eventId.value,
                        eventType = it.eventType.value,
                        category = it.category.name,
                        timestamp = it.timestamp.toString(),
                        correlationId = it.correlationId.value
                    )
                }
            )
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
        }
    }

    private fun toSummaryDto(adapter: Adapter): AdapterSummaryDto {
        val health = adapter.health()
        val metrics = adapter.metrics()
        return AdapterSummaryDto(
            adapterId = adapter.adapterId.value,
            adapterVersion = adapter.adapterVersion,
            state = registry.stateOf(adapter.adapterId)?.name ?: "UNKNOWN",
            channels = adapter.supportedChannels().map { it.type.value },
            capabilities = adapter.supportedCapabilities().map { it.name },
            healthy = health.healthy,
            healthDetails = health.details,
            messagesSent = metrics.messagesSent,
            messagesReceived = metrics.messagesReceived,
            errorCount = metrics.errorCount,
            throttledCount = metrics.throttledCount
        )
    }

    private fun queryParam(exchange: com.sun.net.httpserver.HttpExchange, name: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
    }
}
