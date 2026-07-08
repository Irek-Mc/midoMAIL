# SPEC-0014 — Adapter Lifecycle Contract

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/14-Registry-Adapterow.md, SPEC-0006-Adapter-Contract.md, SPEC-0010-Plugin-SDK-Contract.md

---

# Cel

Dokument definiuje pełną tabelę dozwolonych przejść między stanami adaptera. 10-Core/14-Registry-Adapterow.md, §4 wymienia 8 stanów (Registered, Initializing, Ready, Busy, Degraded, Stopping, Stopped, Failed) i stwierdza, że przejścia są publikowane jako zdarzenia domenowe, ale nie podaje samej tabeli przejść. SPEC-0010-Plugin-SDK-Contract.md doprecyzowuje jedynie happy path (Registered → Initializing → Ready; Stopping → Stopped). Ujawniło się to jako luka blokująca implementację Fazy 1 (Iteracja 8a).

---

# Tabela przejść

| Ze stanu | Do stanu | Uwaga |
|---|---|---|
| Registered | Initializing | happy path (SPEC-0010, §Przepływ rejestracji) |
| Initializing | Ready | happy path — `adapter.start()` powiodło się (SPEC-0010) |
| Initializing | Failed | inicjalizacja nieudana |
| Ready | Busy | rozpoczęcie faktycznej pracy (send/receive) |
| Busy | Ready | zakończenie pracy, powrót do gotowości |
| Ready | Degraded | wykryto problem (np. `HealthProvider.health() == false`) bez pełnej awarii |
| Busy | Degraded | problem wykryty w trakcie pracy |
| Degraded | Ready | odzyskanie sprawności |
| Degraded | Busy | praca kontynuowana mimo zdegradowanego stanu |
| Ready, Busy, Degraded, Initializing | Failed | nieodwracalna awaria w dowolnym momencie pracy |
| Ready, Busy, Degraded, Failed | Stopping | zatrzymanie zainicjowane administracyjnie w dowolnym momencie |
| Stopping | Stopped | happy path (SPEC-0010, §Przepływ rejestracji) — `adapter.stop()` |

**Stany końcowe:** `Stopped` — nie ma przejść wychodzących; ponowne użycie adaptera wymaga nowej rejestracji (`Registry.register`), nie przejścia stanu. `Failed` nie jest stanem końcowym — jedyne wyjście z `Failed` to `Stopping` (administracyjne zatrzymanie), nie bezpośredni powrót do `Ready`.

---

# Zakres w Fazie 1 — AdapterLifecycle

Registry (10-Core/14-Registry-Adapterow.md) faktycznie wywołuje na adapterze wyłącznie `start()`
(przejście Initializing → Ready) i `stop()` (przejście Stopping → Stopped) — pozostałe metody
pełnego interfejsu `Adapter` z SPEC-0010-Plugin-SDK-Contract.md (`health()`, `metrics()`, `send()`,
`supportedChannels()`, `supportedCapabilities()`) są wywoływane przez inne komponenty (Health
Monitor, Gateway Engine), nie przez Registry, i wymagają typów (`HealthStatus`, `Metrics`)
nieokreślonych jeszcze w żadnym dokumencie.

W Fazie 1 Registry zależy wyłącznie od wąskiego interfejsu:

```kotlin
interface AdapterLifecycle {
    fun start()
    fun stop()
}
```

Pełny interfejs `Adapter` (SPEC-0010) — którego `AdapterLifecycle` jest podzbiorem — powstaje w
fazie, w której pojawi się pierwszy rzeczywisty adapter (Faza 2+). `HealthStatus` i `Metrics`
doprecyzowane w SPEC-0015-Adapter-Observability-Contract.md.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/14-Registry-Adapterow.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
- 91-Specification/SPEC-0015-Adapter-Observability-Contract.md
