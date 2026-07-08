package midomail.adapter.websocket

import midomail.domain.message.ChannelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WebSocketMessageMapperTest {

    private val mapper = WebSocketMessageMapper(ChannelType("websocket"))

    @Test
    fun `fromText maps the raw frame to payload content unchanged`() {
        val message = mapper.fromText("hello world")

        assertEquals("hello world", message.payload.content)
        assertEquals(ChannelType("websocket"), message.source.type)
    }

    @Test
    fun `fromText generates a unique ExternalReference per call, since WebSocket has no natural message id`() {
        val first = mapper.fromText("a")
        val second = mapper.fromText("a")

        assertNotEquals(first.identity.externalReference, second.identity.externalReference)
        assertNotEquals(first.identity.messageId, second.identity.messageId)
    }

    @Test
    fun `toText returns the payload content for sending`() {
        val message = mapper.fromText("round trip")

        assertEquals("round trip", mapper.toText(message))
    }
}
