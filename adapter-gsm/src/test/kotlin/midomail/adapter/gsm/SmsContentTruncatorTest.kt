package midomail.adapter.gsm

import midomail.domain.message.CorrelationId
import midomail.domain.port.memory.InMemoryEventPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmsContentTruncatorTest {

    private val correlationId = CorrelationId("11111111-1111-1111-1111-111111111111")

    @Test
    fun `content within a single GSM-7 segment is not truncated`() {
        val eventPublisher = InMemoryEventPublisher()
        val truncator = SmsContentTruncator(maxSegments = 1, eventPublisher = eventPublisher)
        val content = "a".repeat(100)

        val result = truncator.truncateIfNeeded(content, correlationId)

        assertEquals(content, result)
        assertTrue(eventPublisher.events().isEmpty())
    }

    @Test
    fun `content exceeding a single GSM-7 segment is truncated with an indicator`() {
        val eventPublisher = InMemoryEventPublisher()
        val truncator = SmsContentTruncator(maxSegments = 1, eventPublisher = eventPublisher)
        val content = "a".repeat(200)

        val result = truncator.truncateIfNeeded(content, correlationId)

        assertEquals(160, result.length)
        assertTrue(result.endsWith("…"))
        assertEquals("a".repeat(159) + "…", result)
    }

    @Test
    fun `content fitting exactly within the configured multi-segment budget is not truncated`() {
        val eventPublisher = InMemoryEventPublisher()
        val truncator = SmsContentTruncator(maxSegments = 2, eventPublisher = eventPublisher)
        val content = "a".repeat(300)

        val result = truncator.truncateIfNeeded(content, correlationId)

        assertEquals(content, result)
        assertTrue(eventPublisher.events().isEmpty())
    }

    @Test
    fun `content exceeding the configured multi-segment budget is truncated to fit exactly`() {
        val eventPublisher = InMemoryEventPublisher()
        val truncator = SmsContentTruncator(maxSegments = 2, eventPublisher = eventPublisher)
        val content = "a".repeat(400)

        val result = truncator.truncateIfNeeded(content, correlationId)

        assertEquals(306, result.length)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun `content requiring UCS-2 due to Polish characters uses the shorter segment capacity`() {
        val eventPublisher = InMemoryEventPublisher()
        val truncator = SmsContentTruncator(maxSegments = 1, eventPublisher = eventPublisher)
        val content = "ą".repeat(80)

        val result = truncator.truncateIfNeeded(content, correlationId)

        assertEquals(70, result.length)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun `truncation publishes a domain event with original and truncated lengths`() {
        val eventPublisher = InMemoryEventPublisher()
        val truncator = SmsContentTruncator(maxSegments = 1, eventPublisher = eventPublisher)
        val content = "a".repeat(200)

        truncator.truncateIfNeeded(content, correlationId)

        val event = eventPublisher.events().single()
        assertEquals("sms.content_truncated", event.eventType.value)
        assertEquals(correlationId, event.correlationId)
        val payload = event.payload as SmsContentTruncated
        assertEquals(200, payload.originalLength)
        assertEquals(160, payload.truncatedLength)
    }
}
