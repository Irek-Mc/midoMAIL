package midomail.domain.event

import midomail.domain.message.CausationId
import midomail.domain.message.CorrelationId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Potwierdza kontrakt Event z SPEC-0003-Event-Model.md, §Minimalny kontrakt zdarzenia
 * i §Kategorie zdarzeń.
 */
class EventTest {

    private fun createEvent(
        causationId: CausationId? = null,
        payload: Any = "payload"
    ): Event = Event(
        eventId = EventId("event-1"),
        eventType = EventType("domain.message_received"),
        eventVersion = EventVersion("1.0"),
        category = EventCategory.DOMAIN,
        timestamp = Instant.parse("2026-07-05T08:00:00Z"),
        correlationId = CorrelationId("correlation-1"),
        causationId = causationId,
        sourceComponent = SourceComponent("GatewayEngine"),
        payload = payload
    )

    @Test
    fun `EventId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { EventId("") }
    }

    @Test
    fun `EventType rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { EventType("") }
    }

    @Test
    fun `EventVersion rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { EventVersion("") }
    }

    @Test
    fun `SourceComponent rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { SourceComponent("") }
    }

    @Test
    fun `EventCategory contains exactly the six documented values`() {
        assertEquals(
            listOf(
                EventCategory.DOMAIN,
                EventCategory.PROCESSING,
                EventCategory.ADAPTER,
                EventCategory.INFRASTRUCTURE,
                EventCategory.DIAGNOSTIC,
                EventCategory.ADMINISTRATIVE
            ),
            EventCategory.entries
        )
    }

    @Test
    fun `causationId defaults to null when not specified`() {
        val event = createEvent()

        assertNull(event.causationId)
    }

    @Test
    fun `event can carry an arbitrary payload appropriate to its category`() {
        val event = createEvent(payload = mapOf("adapterId" to "email-primary"))

        assertEquals(mapOf("adapterId" to "email-primary"), event.payload)
    }

    @Test
    fun `two events built from the same values are equal - events are immutable value objects`() {
        val first = createEvent()
        val second = createEvent()

        assertEquals(first, second)
    }
}
