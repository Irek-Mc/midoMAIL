package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
import midomail.domain.message.GatewayMessage
import midomail.domain.port.MessageMetadata

@Serializable
data class MessageDto(
    val messageId: String,
    val correlationId: String,
    val causationId: String? = null,
    val externalReference: String,
    val sourceChannel: String,
    val sourceAdapter: String? = null,
    val destinationChannel: String,
    val destinationAdapter: String? = null,
    val messagePriority: String,
    val processingState: String,
    val content: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MessagePageDto(val items: List<MessageDto>, val nextCursor: String? = null)

@Serializable
data class ReprocessResultDto(val outcome: String, val newMessageId: String? = null, val reason: String? = null)

/**
 * [metadata] opcjonalne — `createdAt`/`updatedAt` (62-Komunikaty.md §2) żyją w `MessageStore`
 * osobno od `GatewayMessage` (Iteracja 6.23, `MessageStore.metadataFor`), wołający dostarcza je
 * jeśli dostępne.
 */
fun GatewayMessage.toDto(metadata: MessageMetadata? = null): MessageDto = MessageDto(
    messageId = identity.messageId.value,
    correlationId = identity.correlationId.value,
    causationId = identity.causationId?.value,
    externalReference = identity.externalReference.value,
    sourceChannel = source.type.value,
    sourceAdapter = source.adapterId?.value,
    destinationChannel = destination.type.value,
    destinationAdapter = destination.adapterId?.value,
    messagePriority = identity.messagePriority.name,
    processingState = processingContext.processingState.name,
    content = payload.content,
    createdAt = metadata?.createdAt?.toString(),
    updatedAt = metadata?.updatedAt?.toString()
)
