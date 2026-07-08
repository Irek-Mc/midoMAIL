# SPEC-0023 — Diagnostics/Event Store Contract

**Status:** Accepted
**Powiązane dokumenty:** 30-Infrastructure/36-Diagnostyka.md, 60-User-Interface/67-Diagnostyka.md, 10-Core/15-Event-Bus.md, SPEC-0003-Event-Model.md, SPEC-0009-Message-Store-Contract.md

---

# Cel

67-Diagnostyka.md §3 wymaga widoku „Ślad komunikatu" obejmującego m.in. „Zdarzenia domenowe" powiązane z konkretnym `CorrelationId`, a 36-Diagnostyka.md §4 wymaga „historii zdarzeń" jako części zakresu diagnostyki. Żaden istniejący port tego nie umożliwia: `EventPublisher` (SPEC-0003) jest **wyłącznie publikacją** — brak odczytu/zapytania. `MessageStore` przechowuje `GatewayMessage`, nie dowolne `Event`. Rozstrzygane tutaj.

---

# Decyzja: nowy port `EventStore`, `EventPublisher` pozostaje niezmieniony

`EventPublisher.publish()` (SPEC-0003) jest zamrożonym, prostym kontraktem publish-only — nie jest rozszerzany o `subscribe()`/`query()` (zbyt duży promień rażenia dla istniejącego, ustabilizowanego portu). Zamiast tego: nowy, równoległy port `EventStore`, wzorowany na `MessageStore` (SPEC-0009) — ten sam kształt filtrowania/paginacji, inny model danych.

```kotlin
package midomail.domain.port

data class EventQueryFilter(
    val correlationId: midomail.domain.message.CorrelationId? = null,
    val eventType: midomail.domain.event.EventType? = null,
    val category: midomail.domain.event.EventCategory? = null,
    val sourceComponent: midomail.domain.event.SourceComponent? = null,
    val createdAfter: java.time.Instant? = null,
    val createdBefore: java.time.Instant? = null
)

interface EventStore {
    fun record(event: midomail.domain.event.Event)
    fun query(filter: EventQueryFilter, page: PageRequest): Page<midomail.domain.event.Event>
}
```

`PageRequest`/`Page<T>` reużyte z `MessageStore.kt` (ten sam generyczny kształt paginacji kursorowej, SPEC-0009 §Paginacja) — nie duplikowane.

Komponenty publikujące zdarzenia (Registry, GatewayEngine, HealthMonitor) wołają **oba** porty — `EventPublisher.publish()` (niezmieniony) ORAZ `EventStore.record()` (nowy) — nie jeden zamiast drugiego. `EventStore` to trwałe (w Fazie 4: in-memory, zgodnie z ograniczeniem odziedziczonym z Faz 1–2) repozytorium do zapytań diagnostycznych; `EventPublisher` pozostaje mechanizmem czasu rzeczywistego (Event Bus, 15-Event-Bus.md).

---

# Diagnostics Facade — jawnie poza i w zakresie

**W zakresie Fazy 4 (kontrakt Core):** `DiagnosticsFacade` łączący trzy już istniejące źródła prawdy:
- `MessageStore.findByCorrelationId`/`findByExternalReference` — „Ślad komunikatu" (67-Diagnostyka.md §3).
- `EventStore.query(correlationId)` — „Zdarzenia domenowe" tego samego śladu.
- `Registry.stateOf` — „Stan komponentów" (67-Diagnostyka.md §3).

**Poza zakresem Fazy 4** (67-Diagnostyka.md §4: „Wyszukiwanie", „Eksport raportu diagnostycznego" to operacje EKRANU, nie kontraktu): jakikolwiek interfejs użytkownika wyszukiwania/eksportu — to Faza 6 (Roadmap §8: „UI nigdy nie poprzedza Core"). Faza 4 dostarcza wyłącznie zapytywalny kontrakt Core, z którego przyszły ekran będzie czytał, nie wylicza niczego lokalnie.

---

# Dokumenty powiązane

- 10-Core/15-Event-Bus.md
- 30-Infrastructure/36-Diagnostyka.md
- 60-User-Interface/67-Diagnostyka.md
- 91-Specification/SPEC-0003-Event-Model.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
