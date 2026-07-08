package midomail.domain.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Potwierdza kontrakt Identity z SPEC-0001-GatewayMessage.md, §Identity:
 * MessageId/CorrelationId/SchemaVersion/ExternalReference obowiązkowe i niepuste,
 * CausationId opcjonalne (10-Core/12-GatewayMessage.md, §4),
 * MessagePriority domyślnie NORMAL (ADR-0005-Message-Priority.md).
 */
class IdentityTest {

    private fun createIdentity(
        causationId: CausationId? = null,
        messagePriority: MessagePriority = MessagePriority.NORMAL
    ): Identity = Identity(
        messageId = MessageId("message-1"),
        correlationId = CorrelationId("correlation-1"),
        causationId = causationId,
        schemaVersion = SchemaVersion("2.0"),
        externalReference = ExternalReference("<abc@localhost>"),
        messagePriority = messagePriority
    )

    @Test
    fun `messagePriority defaults to NORMAL when not specified`() {
        val identity = Identity(
            messageId = MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference("<abc@localhost>")
        )

        assertEquals(MessagePriority.NORMAL, identity.messagePriority)
    }

    @Test
    fun `causationId defaults to null when not specified`() {
        val identity = createIdentity()

        assertNull(identity.causationId)
    }

    @Test
    fun `causationId can be provided explicitly`() {
        val identity = createIdentity(causationId = CausationId("cause-1"))

        assertEquals(CausationId("cause-1"), identity.causationId)
    }

    @Test
    fun `messagePriority can be overridden explicitly`() {
        val identity = createIdentity(messagePriority = MessagePriority.CRITICAL)

        assertEquals(MessagePriority.CRITICAL, identity.messagePriority)
    }

    @Test
    fun `MessageId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { MessageId("") }
        assertFailsWith<IllegalArgumentException> { MessageId("   ") }
    }

    @Test
    fun `CorrelationId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { CorrelationId("") }
    }

    @Test
    fun `CausationId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { CausationId("") }
    }

    @Test
    fun `SchemaVersion rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { SchemaVersion("") }
    }

    @Test
    fun `ExternalReference rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { ExternalReference("") }
    }

    @Test
    fun `MessageId and ExternalReference are distinct types not interchangeable`() {
        val messageId = MessageId("shared-value")
        val externalReference = ExternalReference("shared-value")

        // Wartości tekstowe mogą być identyczne, ale typy pozostają odrębne — kompilator
        // nie pozwoliłby przypisać jednego w miejsce drugiego (SPEC-0001, §Identity).
        assertEquals("shared-value", messageId.value)
        assertEquals("shared-value", externalReference.value)
    }
}
