# ADR-0018 — Registry: enumeracja zarejestrowanych adapterów

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`Registry` (10-Core/14-Registry-Adapterow.md) świadomie udostępnia wyłącznie `stateOf(adapterId): AdapterState?` — zapytanie o stan JEDNEGO, znanego z góry adaptera, nie enumerację wszystkich zarejestrowanych. W Fazie 4 (SPEC-0020-Health-Aggregation-Contract.md, §Skąd HealthMonitor bierze adaptery) ten sam brak został obejściowo rozwiązany przez wstrzyknięcie stałej `List<Adapter>` bezpośrednio z punktu kompozycji, bez zmiany w `Registry` — wystarczające, bo zestaw adapterów był znany i stały od startu procesu.

Faza 5 (Adapter REST/CLI, SPEC-0024-Administrative-API-Contract.md, §Odczyt stanu) wymaga czegoś innego: widoku listy adapterów (`64-Adaptery.md` §2) odzwierciedlającego ŻYWY, zmienny w czasie stan `Registry` — w tym przyszłe operacje włącz/wyłącz/usuń (§4), które zmieniają członkostwo w trakcie działania procesu. Wstrzyknięcie stałej listy (wzorzec z Fazy 4) nie wystarcza, bo lista musi się zmieniać wraz z rzeczywistym stanem Registry, nie być zamrożona przy starcie.

## Decyzja

`Registry` zyskuje nową, addytywną metodę:

```kotlin
fun registeredAdapterIds(): Set<AdapterId>
```

Zwraca zbiór `AdapterId` aktualnie znanych `Registry` (od `register()` do `unregister()`, niezależnie od bieżącego `AdapterState` — także `FAILED`/`STOPPED`, dopóki nie zostały jawnie wyrejestrowane). Nie zwraca instancji `Adapter`/`AdapterLifecycle` — Registry nadal nie eksponuje ich (ta granica pozostaje nienaruszona, `AdapterRegistryOutbound`/wstrzyknięcie z Fazy 3/4 nadal jest właściwym mechanizmem tam, gdzie potrzebna jest żywa instancja, nie tylko identyfikator).

## Konsekwencje

- Amendment addytywny, kompatybilny wstecznie — żaden istniejący wywołujący `Registry` się nie psuje.
- Adapter REST/CLI (Faza 5) mogą zbudować widok listy przez `registeredAdapterIds()` + `stateOf()` dla każdego, bez potrzeby własnego, równoległego rejestru stanu.
- `HealthMonitor`/`StatisticsAggregator` (Faza 4) mogłyby teoretycznie przejść na tę metodę zamiast wstrzykiwanej listy — **świadomie nie migrowane w tej iteracji** (działający kod, brak potrzeby zmiany bez powodu; ich wzorzec pozostaje poprawny dla przypadku stałego zestawu adapterów znanego przy starcie).

## Dokumenty powiązane

- 10-Core/14-Registry-Adapterow.md
- 60-User-Interface/64-Adaptery.md
- 91-Specification/SPEC-0020-Health-Aggregation-Contract.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
