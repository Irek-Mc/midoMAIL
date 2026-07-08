package midomail.domain.diagnostics

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventStore
import java.time.Instant
import java.util.UUID

/** Ładunek wpisu logu (payload `Event` o kategorii `DIAGNOSTIC`). */
data class LogEntry(val level: LogLevel, val message: String, val throwableMessage: String?)

/**
 * Implementacja domyślna [SystemLogger] — zasila [EventStore] (SPEC-0023), nie
 * `EventPublisher` (log historyczny/zapytywalny, nie zdarzenie czasu rzeczywistego).
 *
 * [redact] — hook redakcji danych wrażliwych (33-Logowanie.md §4: „ochrona danych wrażliwych"),
 * domyślnie tożsamościowy (brak redakcji). Dokumentacja nie precyzuje konkretnych reguł redakcji
 * (jakie wzorce, jakie pola) — pełny silnik redakcji świadomie poza zakresem tej iteracji, tylko
 * punkt zaczepienia dla przyszłej implementacji.
 *
 * 33-Logowanie.md §5: „Awaria systemu logowania nie może blokować pracy Gateway" — wyjątek z
 * [EventStore.record] jest łapany i ignorowany, nigdy nie propaguje się do wywołującego.
 */
class EventStoreSystemLogger(
    private val eventStore: EventStore,
    private val sourceComponent: SourceComponent,
    private val redact: (String) -> String = { it }
) : SystemLogger {

    override fun log(level: LogLevel, message: String, correlationId: CorrelationId?, throwable: Throwable?) {
        try {
            eventStore.record(
                Event(
                    eventId = EventId(UUID.randomUUID().toString()),
                    eventType = EventType("log.${level.name.lowercase()}"),
                    eventVersion = EventVersion("1.0"),
                    category = EventCategory.DIAGNOSTIC,
                    timestamp = Instant.now(),
                    correlationId = correlationId ?: CorrelationId(UUID.randomUUID().toString()),
                    sourceComponent = sourceComponent,
                    payload = LogEntry(level, redact(message), throwable?.message?.let { redact(it) })
                )
            )
        } catch (_: Exception) {
            // Celowo ignorowane (33-Logowanie.md §5) - awaria logowania nie może blokować Gateway.
        }
    }
}
