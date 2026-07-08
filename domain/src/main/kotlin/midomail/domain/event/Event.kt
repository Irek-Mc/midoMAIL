package midomail.domain.event

import midomail.domain.message.CausationId
import midomail.domain.message.CorrelationId
import java.time.Instant

@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId nie może być pusty" }
    }
}

/** Otwarty (rozszerzalny) identyfikator typu zdarzenia — nowa wartość nie wymaga zmiany kontraktu. */
@JvmInline
value class EventType(val value: String) {
    init {
        require(value.isNotBlank()) { "EventType nie może być pusty" }
    }
}

@JvmInline
value class EventVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "EventVersion nie może być pusty" }
    }
}

@JvmInline
value class SourceComponent(val value: String) {
    init {
        require(value.isNotBlank()) { "SourceComponent nie może być pusty" }
    }
}

/** Kategorie zdarzeń (SPEC-0003-Event-Model.md, §Kategorie zdarzeń) — zamknięta taksonomia. */
enum class EventCategory {
    DOMAIN,
    PROCESSING,
    ADAPTER,
    INFRASTRUCTURE,
    DIAGNOSTIC,
    ADMINISTRATIVE
}

/**
 * Zdarzenie domenowe (10-Core/15-Event-Bus.md; SPEC-0003-Event-Model.md).
 *
 * Zdarzenia są niemutowalne i opisują fakty, które już wystąpiły. [payload] jest celowo
 * nieograniczonego typu — różne kategorie zdarzeń niosą fundamentalnie różne dane (np. Domain
 * Event może nieść GatewayMessage, Adapter Event — stan adaptera).
 */
data class Event(
    val eventId: EventId,
    val eventType: EventType,
    val eventVersion: EventVersion,
    val category: EventCategory,
    val timestamp: Instant,
    val correlationId: CorrelationId,
    val causationId: CausationId? = null,
    val sourceComponent: SourceComponent,
    val payload: Any
)
