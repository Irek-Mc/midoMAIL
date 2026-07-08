package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.RoutingDecisionDto
import midomail.adapter.rest.dto.RoutingRuleChangeDto
import midomail.adapter.rest.dto.RoutingRuleDto
import midomail.adapter.rest.dto.SimulateRoutingRequestDto
import midomail.adapter.rest.dto.toDomain
import midomail.adapter.rest.dto.toDto
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.routing.RoutingDecision
import midomail.domain.routing.RuleId
import java.util.UUID

/**
 * Endpointy administracji regułami routingu (SPEC-0024, §4 Routing — 63-Routing.md §3-4).
 * Dodaj/Edytuj/Usuń delegują do [RoutingRuleAdministration] (ADR-0021); symulator wywołuje
 * `RoutingEngine.route()` na syntetycznej wiadomości zbudowanej wyłącznie z pól, po których
 * `RoutingConditions` faktycznie dopasowuje (63-Routing.md §4: „bez wysyłania rzeczywistego
 * komunikatu" — żadnych efektów ubocznych, ta wiadomość nigdy nie trafia do `GatewayEngine`).
 */
class RoutingEndpoints(private val routingRuleAdministration: RoutingRuleAdministration) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("POST", "/routing/rules") { exchange ->
            val dto = readBody<RoutingRuleDto>(exchange) ?: run {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowe ciało żądania")
                return@route
            }
            try {
                routingRuleAdministration.add(dto.toDomain())
                AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
            } catch (exception: IllegalArgumentException) {
                AdminHttpServer.respond(exchange, 409, exception.message ?: "Konflikt")
            }
        }

        server.route("POST", "/routing/rules/update") { exchange ->
            val ruleId = queryParam(exchange, "ruleId")
            if (ruleId == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: ruleId")
                return@route
            }
            val dto = readBody<RoutingRuleDto>(exchange) ?: run {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowe ciało żądania")
                return@route
            }
            try {
                routingRuleAdministration.update(RuleId(ruleId), dto.toDomain())
                val updated = routingRuleAdministration.list().first { it.ruleId.value == ruleId }
                AdminHttpServer.respond(exchange, 200, json.encodeToString(updated.toDto()))
            } catch (exception: IllegalArgumentException) {
                AdminHttpServer.respond(exchange, 404, exception.message ?: "Nie znaleziono")
            }
        }

        server.route("DELETE", "/routing/rules") { exchange ->
            val ruleId = queryParam(exchange, "ruleId")
            if (ruleId == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: ruleId")
                return@route
            }
            routingRuleAdministration.remove(RuleId(ruleId))
            AdminHttpServer.respond(exchange, 200, "OK")
        }

        server.route("GET", "/routing/rules/history") { exchange ->
            val history = routingRuleAdministration.history().map { it.toDto() }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(history))
        }

        server.route("POST", "/routing/simulate") { exchange ->
            val request = readBody<SimulateRoutingRequestDto>(exchange) ?: run {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowe ciało żądania")
                return@route
            }
            val messagePriorityBefore = midomail.domain.message.MessagePriority.valueOf(request.messagePriority)
            val message = syntheticMessage(request.sourceChannel, request.destinationChannel, messagePriorityBefore)
            val decision = routingRuleAdministration.buildEngine().route(message)
            val dto = when (decision) {
                is RoutingDecision.Routed -> RoutingDecisionDto(
                    matched = true,
                    targetChannel = decision.targetChannel.value,
                    targetAdapter = decision.targetAdapter.value,
                    deliveryPolicy = decision.deliveryPolicy.value,
                    messagePriorityBefore = messagePriorityBefore.name,
                    messagePriorityAfter = decision.messagePriority.name
                )
                RoutingDecision.NoMatch -> RoutingDecisionDto(matched = false, messagePriorityBefore = messagePriorityBefore.name)
            }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
        }
    }

    private fun syntheticMessage(
        sourceChannel: String,
        destinationChannel: String,
        messagePriority: midomail.domain.message.MessagePriority
    ): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId(UUID.randomUUID().toString()),
            correlationId = CorrelationId(UUID.randomUUID().toString()),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference("simulate-${UUID.randomUUID()}"),
            messagePriority = messagePriority
        ),
        source = Channel(type = ChannelType(sourceChannel)),
        destination = Channel(type = ChannelType(destinationChannel)),
        payload = Payload(content = "")
    )

    private inline fun <reified T> readBody(exchange: com.sun.net.httpserver.HttpExchange): T? = try {
        json.decodeFromString<T>(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    private fun queryParam(exchange: com.sun.net.httpserver.HttpExchange, name: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
    }
}
