# SPEC-0018 — Alert Model Contract

**Status:** Accepted
**Powiązane dokumenty:** 00-Foundation/06-Glossary.md, 30-Infrastructure/35-Health-Monitor.md, 30-Infrastructure/38-Powiadomienia.md, SPEC-0003-Event-Model.md

---

# Cel

30-Infrastructure/35-Health-Monitor.md §6 definiuje model Alertu w prozie: „Alert posiada identyfikator, źródło, czas wystąpienia, status oraz zalecane działania", z poziomami Info/Warning/Error/Critical — ale nie precyzuje typów/pól. 00-Foundation/06-Glossary.md potwierdza, że Alert może być generowany przez **dwa** źródła (Health Monitor lub Error Handling), więc model musi być wspólny dla obu, nie przypisany wyłącznie do jednego komponentu. Ta luka blokuje Iterację 4.3 (HealthMonitor) i 4.4b (klasyfikacja błędów) — rozstrzygana tutaj, przed kodem, zgodnie z ustaloną metodyką projektu (analogicznie do SPEC-0013/0014/0015 w poprzednich fazach).

---

# Model

```kotlin
package midomail.domain.health

@JvmInline
value class AlertId(val value: String) {
    init { require(value.isNotBlank()) { "AlertId nie może być pusty" } }
}

/** Cztery poziomy z 35-Health-Monitor.md §6 — zamknięta taksonomia, rozszerzenie wymaga nowego ADR. */
enum class AlertLevel { INFO, WARNING, ERROR, CRITICAL }

/**
 * ACTIVE — Alert wygenerowany, oczekuje reakcji administratora; podlega eskalacji
 * (38-Powiadomienia.md §5) dopóki nie przejdzie do ACKNOWLEDGED.
 * ACKNOWLEDGED — administrator potwierdził Alert (60-User-Interface/66-Monitoring.md,
 * §Operacje: „Potwierdzenie alertu") — eskalacja zatrzymuje się natychmiast.
 * RESOLVED — warunek leżący u podstaw Alertu ustąpił samoistnie (np. Health Monitor
 * ponownie widzi komponent zdrowy) — niezależne od ACKNOWLEDGED, Alert może zostać
 * rozwiązany bez nigdy niepotwierdzonego przez administratora, i odwrotnie.
 */
enum class AlertStatus { ACTIVE, ACKNOWLEDGED, RESOLVED }

data class Alert(
    val alertId: AlertId,
    val level: AlertLevel,
    val source: midomail.domain.event.SourceComponent,
    val timestamp: java.time.Instant,
    val status: AlertStatus,
    val recommendedAction: String? = null,
    val correlationId: midomail.domain.message.CorrelationId? = null
)
```

`source` — reużyty `SourceComponent` z `domain.event` (SPEC-0003-Event-Model.md), nie duplikowany typ — Alert i Event dzielą to samo pojęcie „komponent, który to zgłosił".

`correlationId` — opcjonalny: Alert wygenerowany przez Error Handling w kontekście konkretnej wiadomości (np. `GatewayError` podczas `GatewayEngine.receive()`) niesie jej `CorrelationId`; Alert wygenerowany przez Health Monitor z tytułu stanu komponentu (nie konkretnej wiadomości) go nie niesie — `null`.

`recommendedAction` — zwięzły, czytelny dla człowieka opis (np. „Sprawdź połączenie sieciowe adaptera email-primary"), nie ustrukturyzowany kod akcji — 35-Health-Monitor.md §6 nie precyzuje formatu poza „zalecane działania", więc pozostaje wolnym tekstem.

---

# Zasady zgodności

Rozszerzenie `Alert`/`AlertLevel`/`AlertStatus` o dodatkowe pola/wartości wymaga nowego ADR — analogicznie do `HealthStatus`/`Metrics` (SPEC-0015, §Zasady zgodności).

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/35-Health-Monitor.md
- 30-Infrastructure/38-Powiadomienia.md
- 60-User-Interface/66-Monitoring.md
- 91-Specification/SPEC-0003-Event-Model.md
- 91-Specification/SPEC-0015-Adapter-Observability-Contract.md
