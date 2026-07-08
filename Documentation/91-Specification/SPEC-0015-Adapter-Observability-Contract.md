# SPEC-0015 — Adapter Observability Contract

**Status:** Accepted
**Powiązane dokumenty:** SPEC-0006-Adapter-Contract.md, SPEC-0010-Plugin-SDK-Contract.md, SPEC-0011-Rate-Limiting-Contract.md, SPEC-0014-Adapter-Lifecycle-Contract.md

---

# Cel

Dokument definiuje pola `HealthStatus` i `Metrics` — SPEC-0006-Adapter-Contract.md, §Minimalny kontrakt wymienia je jako obowiązkową część kontraktu każdego adaptera, ale żaden dokument nie precyzował ich pól. Ujawniło się to jako luka blokująca wprowadzenie pełnego interfejsu `Adapter` z SPEC-0010 (Faza 2, Iteracja 2.1) — SPEC-0014, §Zakres w Fazie 1 świadomie odłożyła to rozstrzygnięcie do fazy, w której pojawi się pierwszy rzeczywisty adapter.

---

# HealthStatus

```kotlin
data class HealthStatus(
    val healthy: Boolean,
    val details: String? = null
)
```

`healthy` odzwierciedla bieżącą ocenę gotowości adaptera (10-Core/14-Registry-Adapterow.md, §4: adapter w stanie `Degraded` zwraca `healthy = false`; w `Ready`/`Busy` — `true`). `details` (opcjonalny) niesie zwięzłą przyczynę degradacji, przeznaczoną dla diagnostyki (60-User-Interface/67-Diagnostyka.md) — np. „IMAP connection timeout".

---

# Metrics

```kotlin
data class Metrics(
    val availableTokens: Long?,
    val throttledCount: Long,
    val cumulativeThrottlingEvents: Long,
    val messagesSent: Long,
    val messagesReceived: Long,
    val errorCount: Long
)
```

`availableTokens`, `throttledCount`, `cumulativeThrottlingEvents` — stan Rate Limitera, wprost wymagany przez SPEC-0006, §Minimalny kontrakt i SPEC-0011-Rate-Limiting-Contract.md, §Obserwowalność. `availableTokens` jest `null`, jeśli dla adaptera nie skonfigurowano limitu (SPEC-0011: para bez konfiguracji nie ma limitu).

`messagesSent`, `messagesReceived`, `errorCount` — bezpośrednio z SPEC-0006, §Odpowiedzialność adaptera: „raportowanie stanu i błędów" — minimalne, jednoznacznie uzasadnione liczniki bez dalszej, niedokumentowanej struktury.

---

# Zasady zgodności

Rozszerzenie `HealthStatus`/`Metrics` o dodatkowe pola wymaga nowego ADR — analogicznie do innych zamrożonych kontraktów Core (SPEC-0001, §Zasady zgodności).

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md
- 91-Specification/SPEC-0014-Adapter-Lifecycle-Contract.md
