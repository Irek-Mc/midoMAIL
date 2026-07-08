package midomail.domain.health

import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import java.time.Instant

@JvmInline
value class AlertId(val value: String) {
    init {
        require(value.isNotBlank()) { "AlertId nie może być pusty" }
    }
}

/** Cztery poziomy z 35-Health-Monitor.md §6 — zamknięta taksonomia, rozszerzenie wymaga nowego ADR. */
enum class AlertLevel { INFO, WARNING, ERROR, CRITICAL }

/**
 * `ACTIVE` — Alert wygenerowany, oczekuje reakcji administratora; podlega eskalacji
 * (38-Powiadomienia.md §5) dopóki nie przejdzie do `ACKNOWLEDGED`.
 * `ACKNOWLEDGED` — administrator potwierdził Alert (60-User-Interface/66-Monitoring.md,
 * §Operacje: „Potwierdzenie alertu") — eskalacja zatrzymuje się natychmiast.
 * `RESOLVED` — warunek leżący u podstaw Alertu ustąpił samoistnie (np. Health Monitor
 * ponownie widzi komponent zdrowy) — niezależne od `ACKNOWLEDGED`, Alert może zostać
 * rozwiązany bez nigdy niepotwierdzonego przez administratora, i odwrotnie.
 */
enum class AlertStatus { ACTIVE, ACKNOWLEDGED, RESOLVED }

/**
 * Model Alertu (SPEC-0018-Alert-Model-Contract.md) — generowany przez Health Monitor LUB Error
 * Handling (00-Foundation/06-Glossary.md, hasło „Alert"), nigdy dwie rozbieżne struktury dla obu
 * źródeł.
 *
 * [source] reużywa [SourceComponent] z `domain.event` (SPEC-0003-Event-Model.md) — Alert i Event
 * dzielą to samo pojęcie „komponent, który to zgłosił", nie duplikowany typ.
 *
 * [correlationId] opcjonalny: Alert z tytułu konkretnej wiadomości (np. `GatewayError` podczas
 * `GatewayEngine.receive()`) niesie jej `CorrelationId`; Alert z tytułu stanu komponentu (nie
 * konkretnej wiadomości) go nie niesie.
 */
data class Alert(
    val alertId: AlertId,
    val level: AlertLevel,
    val source: SourceComponent,
    val timestamp: Instant,
    val status: AlertStatus,
    val recommendedAction: String? = null,
    val correlationId: CorrelationId? = null
)
