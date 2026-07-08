package midomail.domain.message

import midomail.domain.processing.ProcessingContext
import midomail.domain.processing.ProcessingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt agregatu GatewayMessage z SPEC-0001-GatewayMessage.md (całość) i
 * 10-Core/12-GatewayMessage.md (całość) — to najważniejszy publiczny kontrakt Fazy 1.
 */
class GatewayMessageTest {

    private fun createIdentity(): Identity = Identity(
        messageId = MessageId("message-1"),
        correlationId = CorrelationId("correlation-1"),
        schemaVersion = SchemaVersion("2.0"),
        externalReference = ExternalReference("<abc@localhost>")
    )

    private fun createMinimalMessage(): GatewayMessage = GatewayMessage(
        identity = createIdentity(),
        source = Channel(type = ChannelType("gsm"), address = "+48500111222"),
        destination = Channel(type = ChannelType("email"), address = "user@example.com"),
        payload = Payload(content = "Treść komunikatu")
    )

    @Test
    fun `attributes defaults to empty map when not specified`() {
        val message = createMinimalMessage()

        assertTrue(message.attributes.isEmpty())
    }

    @Test
    fun `processingContext defaults to ACCEPTED for newly created message`() {
        val message = createMinimalMessage()

        assertEquals(ProcessingState.ACCEPTED, message.processingContext.processingState)
    }

    @Test
    fun `attributes can be provided explicitly`() {
        val message = createMinimalMessage().copy(attributes = mapOf("key" to "value"))

        assertEquals(mapOf("key" to "value"), message.attributes)
    }

    @Test
    fun `two messages built from the same values are equal`() {
        val first = createMinimalMessage()
        val second = createMinimalMessage()

        assertEquals(first, second)
    }

    @Test
    fun `copy produces a new state transition without mutating the original`() {
        val original = createMinimalMessage()

        val advanced = original.copy(
            processingContext = ProcessingContext(ProcessingState.VALIDATED)
        )

        assertEquals(ProcessingState.ACCEPTED, original.processingContext.processingState)
        assertEquals(ProcessingState.VALIDATED, advanced.processingContext.processingState)
        assertEquals(original.identity, advanced.identity)
        assertEquals(original.payload, advanced.payload)
    }
}
