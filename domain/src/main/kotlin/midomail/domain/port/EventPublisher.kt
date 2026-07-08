package midomail.domain.port

import midomail.domain.event.Event

/**
 * Port publikacji zdarzeń (10-Core/15-Event-Bus.md; SPEC-0003-Event-Model.md).
 *
 * Nadawca nie zna odbiorców zdarzenia (10-Core/15-Event-Bus.md, §3).
 */
interface EventPublisher {
    fun publish(event: Event)
}
