package midomail.adapter.websocket

import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import java.util.UUID

/**
 * Mapowanie ramka tekstowa WebSocket ↔ [GatewayMessage] (SPEC-0026-WebSocket-Adapter-Contract.md,
 * §2). Gołym protokołem WebSocket nie towarzyszy żaden naturalny identyfikator wiadomości (w
 * przeciwieństwie do `Message-ID` z SMTP czy numeru PDU z SMS) — [ExternalReference] generowany
 * jest jako losowy UUID przy każdym odbiorze, świadome uproszczenie udokumentowane w SPEC-0026.
 */
class WebSocketMessageMapper(private val channelType: ChannelType) {

    fun fromText(text: String): GatewayMessage {
        val messageId = MessageId(UUID.randomUUID().toString())
        return GatewayMessage(
            identity = Identity(
                messageId = messageId,
                correlationId = CorrelationId(UUID.randomUUID().toString()),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference(UUID.randomUUID().toString())
            ),
            source = Channel(type = channelType),
            destination = Channel(type = channelType),
            payload = Payload(content = text)
        )
    }

    fun toText(message: GatewayMessage): String = message.payload.content
}
