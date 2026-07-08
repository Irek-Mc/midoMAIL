package midomail.domain.diagnostics

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.PageRequest
import midomail.domain.port.memory.InMemoryEventStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt EventStore (SPEC-0023-Diagnostics-Event-Store-Contract.md).
 */
class InMemoryEventStoreTest {

    private fun event(
        eventId: String,
        correlationId: String,
        eventType: String = "adapter.state_changed",
        timestamp: Instant = Instant.now()
    ): Event = Event(
        eventId = EventId(eventId),
        eventType = EventType(eventType),
        eventVersion = EventVersion("1.0"),
        category = EventCategory.ADAPTER,
        timestamp = timestamp,
        correlationId = CorrelationId(correlationId),
        sourceComponent = SourceComponent("Registry"),
        payload = "test"
    )

    @Test
    fun `query with a correlationId filter returns only matching events`() {
        val store = InMemoryEventStore()
        store.record(event("event-1", correlationId = "correlation-1"))
        store.record(event("event-2", correlationId = "correlation-2"))

        val result = store.query(EventQueryFilter(correlationId = CorrelationId("correlation-1")), PageRequest())

        assertEquals(1, result.items.size)
        assertEquals(EventId("event-1"), result.items.single().eventId)
    }

    @Test
    fun `query with an eventType filter returns only matching events`() {
        val store = InMemoryEventStore()
        store.record(event("event-1", correlationId = "correlation-1", eventType = "adapter.state_changed"))
        store.record(event("event-2", correlationId = "correlation-1", eventType = "domain.message_routed"))

        val result = store.query(EventQueryFilter(eventType = EventType("domain.message_routed")), PageRequest())

        assertEquals(1, result.items.size)
        assertEquals(EventId("event-2"), result.items.single().eventId)
    }

    @Test
    fun `results are sorted by timestamp descending`() {
        val store = InMemoryEventStore()
        val older = Instant.parse("2026-07-06T10:00:00Z")
        val newer = Instant.parse("2026-07-06T11:00:00Z")
        store.record(event("event-old", correlationId = "correlation-1", timestamp = older))
        store.record(event("event-new", correlationId = "correlation-1", timestamp = newer))

        val result = store.query(EventQueryFilter(), PageRequest())

        assertEquals(EventId("event-new"), result.items[0].eventId)
        assertEquals(EventId("event-old"), result.items[1].eventId)
    }

    @Test
    fun `pagination via cursor returns the remaining items on the next page`() {
        val store = InMemoryEventStore()
        repeat(5) { store.record(event("event-$it", correlationId = "correlation-1", timestamp = Instant.now().plusSeconds(it.toLong()))) }

        val firstPage = store.query(EventQueryFilter(), PageRequest(size = 2))
        assertEquals(2, firstPage.items.size)
        assertTrue(firstPage.nextCursor != null)

        val secondPage = store.query(EventQueryFilter(), PageRequest(cursor = firstPage.nextCursor, size = 2))
        assertEquals(2, secondPage.items.size)
        assertTrue(firstPage.items.none { it in secondPage.items })
    }
}
