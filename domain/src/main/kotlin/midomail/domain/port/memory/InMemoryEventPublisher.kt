package midomail.domain.port.memory

import midomail.domain.event.Event
import midomail.domain.port.EventPublisher
import java.util.concurrent.CopyOnWriteArrayList

/** Referencyjna implementacja [EventPublisher] w pamięci (10-Core/15-Event-Bus.md). */
class InMemoryEventPublisher : EventPublisher {

    private val publishedEvents = CopyOnWriteArrayList<Event>()

    override fun publish(event: Event) {
        publishedEvents.add(event)
    }

    fun events(): List<Event> = publishedEvents.toList()
}
