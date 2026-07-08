package midomail.domain.port.memory

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryEventPublisherTest {

    private fun createEvent(eventId: String): Event = Event(
        eventId = EventId(eventId),
        eventType = EventType("domain.test"),
        eventVersion = EventVersion("1.0"),
        category = EventCategory.DOMAIN,
        timestamp = Instant.now(),
        correlationId = CorrelationId("correlation-1"),
        sourceComponent = SourceComponent("Test"),
        payload = "payload"
    )

    @Test
    fun `events returns empty list before any publication`() {
        val publisher = InMemoryEventPublisher()

        assertTrue(publisher.events().isEmpty())
    }

    @Test
    fun `published events are recorded in publication order`() {
        val publisher = InMemoryEventPublisher()

        publisher.publish(createEvent("event-1"))
        publisher.publish(createEvent("event-2"))

        assertEquals(listOf("event-1", "event-2"), publisher.events().map { it.eventId.value })
    }
}
