package midomail.domain.message

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza, że [MessagePriority] zawiera dokładnie cztery wartości zdefiniowane
 * w SPEC-0001-GatewayMessage.md, §MessagePriority.
 */
class MessagePriorityTest {

    @Test
    fun `contains exactly the four documented values`() {
        assertEquals(
            listOf(MessagePriority.LOW, MessagePriority.NORMAL, MessagePriority.HIGH, MessagePriority.CRITICAL),
            MessagePriority.entries
        )
    }
}
