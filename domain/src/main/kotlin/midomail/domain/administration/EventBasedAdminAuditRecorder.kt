package midomail.domain.administration

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventPublisher
import midomail.domain.port.EventStore
import java.time.Instant
import java.util.UUID

/**
 * Implementacja referencyjna [AdminAuditRecorder] — publikuje ORAZ zapisuje dokładnie jedno
 * `Event(category = ADMINISTRATIVE)` na operację, przez oba porty Fazy 4 (`EventPublisher`
 * czasu rzeczywistego, `EventStore` zapytywalny) — ten sam duch co `RecordingEventPublisher` w
 * `:platform-android` (Faza 4), tu jako jawna, nazwana klasa w `:domain` (reużywalna przez
 * `:adapter-rest`/`:adapter-cli`, siostrzane moduły).
 */
class EventBasedAdminAuditRecorder(
    private val eventPublisher: EventPublisher,
    private val eventStore: EventStore,
    private val sourceComponent: SourceComponent
) : AdminAuditRecorder {

    override fun record(operation: String, authenticated: Boolean) {
        val event = Event(
            eventId = EventId(UUID.randomUUID().toString()),
            eventType = EventType(if (authenticated) "admin.operation" else "admin.auth_failure"),
            eventVersion = EventVersion("1.0"),
            category = EventCategory.ADMINISTRATIVE,
            timestamp = Instant.now(),
            correlationId = CorrelationId(UUID.randomUUID().toString()),
            sourceComponent = sourceComponent,
            payload = operation
        )
        eventPublisher.publish(event)
        eventStore.record(event)
    }
}
