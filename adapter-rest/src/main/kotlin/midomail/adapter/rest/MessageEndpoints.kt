package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.MessagePageDto
import midomail.adapter.rest.dto.ReprocessResultDto
import midomail.adapter.rest.dto.toDto
import midomail.domain.adapter.AdapterId
import midomail.domain.administration.MessageReprocessingAdministration
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.MessageId
import midomail.domain.message.MessagePriority
import midomail.domain.port.MessageQueryFilter
import midomail.domain.port.MessageSort
import midomail.domain.port.MessageStore
import midomail.domain.port.PageRequest
import midomail.domain.processing.ProcessingState
import java.time.Instant

/**
 * Endpointy Komunikatów (SPEC-0025, 62-Komunikaty.md) — `GET /messages` (lista/filtr/wyszukiwanie,
 * jednoznacznie odpowiada `MessageStore.query`), `GET /messages/find` (odczyt pojedynczy po
 * `messageId`/`externalReference`/`correlationId`), `POST /messages/reprocess` (ADR-0028).
 */
class MessageEndpoints(
    private val messageStore: MessageStore,
    private val reprocessingAdministration: MessageReprocessingAdministration
) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/messages") { exchange ->
            val filter = MessageQueryFilter(
                channelType = queryParam(exchange, "channelType")?.let { ChannelType(it) },
                adapterId = queryParam(exchange, "adapterId")?.let { AdapterId(it) },
                processingState = queryParam(exchange, "processingState")?.let { ProcessingState.valueOf(it) },
                messagePriority = queryParam(exchange, "messagePriority")?.let { MessagePriority.valueOf(it) },
                createdAfter = queryParam(exchange, "createdAfter")?.let { Instant.parse(it) },
                createdBefore = queryParam(exchange, "createdBefore")?.let { Instant.parse(it) },
                correlationId = queryParam(exchange, "correlationId")?.let { CorrelationId(it) },
                externalReference = queryParam(exchange, "externalReference")?.let { ExternalReference(it) },
                contentSearch = queryParam(exchange, "contentSearch")
            )
            val page = messageStore.query(
                filter = filter,
                sort = MessageSort(),
                page = PageRequest(
                    cursor = queryParam(exchange, "cursor"),
                    size = queryParam(exchange, "size")?.toIntOrNull() ?: 50
                )
            )
            val dto = MessagePageDto(page.items.map { it.toDto(messageStore.metadataFor(it.identity.messageId)) }, page.nextCursor)
            AdminHttpServer.respond(exchange, 200, json.encodeToString(dto))
        }

        server.route("GET", "/messages/find") { exchange ->
            val message = when {
                queryParam(exchange, "messageId") != null ->
                    messageStore.findById(MessageId(queryParam(exchange, "messageId")!!))
                queryParam(exchange, "externalReference") != null ->
                    messageStore.findByExternalReference(ExternalReference(queryParam(exchange, "externalReference")!!))
                queryParam(exchange, "correlationId") != null ->
                    messageStore.findByCorrelationId(CorrelationId(queryParam(exchange, "correlationId")!!)).firstOrNull()
                else -> null
            }
            if (queryParam(exchange, "messageId") == null && queryParam(exchange, "externalReference") == null && queryParam(exchange, "correlationId") == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany jeden z parametrów: messageId, externalReference, correlationId")
                return@route
            }
            if (message == null) {
                AdminHttpServer.respond(exchange, 404, "Nie znaleziono komunikatu")
                return@route
            }
            AdminHttpServer.respond(exchange, 200, json.encodeToString(message.toDto(messageStore.metadataFor(message.identity.messageId))))
        }

        server.route("POST", "/messages/reprocess") { exchange ->
            val externalReference = queryParam(exchange, "externalReference")
            if (externalReference == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: externalReference")
                return@route
            }
            val result = reprocessingAdministration.reprocess(ExternalReference(externalReference))
            val (statusCode, dto) = when (result) {
                is MessageReprocessingAdministration.ReprocessResult.Reprocessed ->
                    200 to ReprocessResultDto("REPROCESSED", newMessageId = result.message.identity.messageId.value)
                MessageReprocessingAdministration.ReprocessResult.NotFound ->
                    404 to ReprocessResultDto("NOT_FOUND")
                is MessageReprocessingAdministration.ReprocessResult.Failed ->
                    409 to ReprocessResultDto("FAILED", reason = result.reason)
            }
            AdminHttpServer.respond(exchange, statusCode, json.encodeToString(dto))
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
