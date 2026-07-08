package midomail.domain.port

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventType
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import java.time.Instant

/** Filtry zapytania (SPEC-0023-Diagnostics-Event-Store-Contract.md), wzorem `MessageQueryFilter`. */
data class EventQueryFilter(
    val correlationId: CorrelationId? = null,
    val eventType: EventType? = null,
    val category: EventCategory? = null,
    val sourceComponent: SourceComponent? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null
)

/**
 * Port zapytywalnej historii zdarzeń (SPEC-0023-Diagnostics-Event-Store-Contract.md) — równoległy
 * do `EventPublisher` (publish-only, niezmieniony), nie jego zamiennik. Komponenty publikujące
 * zdarzenia wołają OBA porty: `EventPublisher.publish()` (czas rzeczywisty, Event Bus) ORAZ
 * `EventStore.record()` (zapytywalne repozytorium do diagnostyki).
 *
 * `PageRequest`/`Page<T>` reużyte z `MessageStore` — ten sam kształt paginacji kursorowej.
 */
interface EventStore {
    fun record(event: Event)
    fun query(filter: EventQueryFilter, page: PageRequest): Page<Event>
}
