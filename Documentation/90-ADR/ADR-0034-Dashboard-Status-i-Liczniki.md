# ADR-0034 — Status Gateway i liczniki Exactly Once dla Dashboardu

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Podczas budowy ekranu Dashboard (Iteracja 6.18) znaleziono trzy sekcje z `60-User-Interface/61-Dashboard.md` §2 bez odpowiadającego kontraktu Core, nieuwzględnione w przeglądzie luk z SPEC-0025 (Iteracja 6.0):

1. **Status Gateway** (Running/Degraded/Stopped, wersja, czas pracy) — `HealthMonitor` (Faza 4) agreguje „worst-of" wyłącznie wewnętrznie, publikując Alert tylko przy PRZEJŚCIU stanu; nie ma metody odczytu bieżącego stanu zagregowanego. Wersja/czas pracy procesu nie istnieją nigdzie jako kontrakt.
2. **Kolejki** (Waiting/Processing/Retrying/Failed wg `MessagePriority`) — §2 wprost: „Model kolejek prezentuje stan zadań Schedulera, a nie Processing State komunikatu". `SchedulerProvider` (SPEC-0013) nie ma dziś ŻADNEJ introspekcji (wyłącznie `schedule`/`cancel`) — dodanie śledzenia stanu per-zadanie to realne rozszerzenie portu, nieprzewidziane w SPEC-0025.
3. **Exactly Once — liczniki** (Processed/Duplicates prevented/Recovered/Failed) — dotyczy też `68-Statystyki.md` §3. `ExactlyOnceEngine.processIfNew()` zwraca `Accepted`/`Duplicate` per wywołanie, nigdzie nie sumując wyników w czasie.

Rozstrzygnięcie przedstawione użytkownikowi przez `AskUserQuestion` — wybrano: zbudować status Gateway i liczniki Exactly Once (Processed/Duplicates prevented — WYŁĄCZNIE te dwa, „Recovered"/„Failed" pozostają poza zakresem `ExactlyOnceEngine`, patrz niżej), odłożyć kolejki Schedulera.

## Decyzja

**1. `HealthMonitor.currentStatus(): AggregateHealthStatus`** — amendment addytywny, odczyt istniejącej wewnętrznej migawki `lastKnownHealthy`:

```kotlin
enum class AggregateHealthStatus { READY, DEGRADED }

fun currentStatus(): AggregateHealthStatus =
    if (adapters.all { lastKnownHealthy[it.adapterId] ?: true }) AggregateHealthStatus.READY
    else AggregateHealthStatus.DEGRADED
```

Adapter bez wpisu w `lastKnownHealthy` (przed pierwszym cyklem `checkOnce()`) traktowany optymistycznie jako zdrowy — ta sama konwencja co już udokumentowana w klasie („baseline przed pierwszym sprawdzeniem to healthy = true").

**2. `GatewayInfo`** — nowa, prosta struktura danych (nie port — brak potrzeby wielu implementacji, ten sam duch co `AlertLevel`/inne proste typy):

```kotlin
data class GatewayInfo(val version: String, val startedAt: Instant)
```

Wypełniana przez punkt kompozycji przy starcie procesu (np. `BuildConfig.VERSION_NAME` na Androidzie + `Instant.now()` przy uruchomieniu `GatewayForegroundService`).

**3. Liczniki w `ExactlyOnceEngine`** — amendment addytywny, zliczanie wewnątrz istniejącej metody:

```kotlin
data class ExactlyOnceCounters(val processed: Long, val duplicatesPrevented: Long)

class ExactlyOnceEngine(private val messageStore: MessageStore) {
    private val processedCount = AtomicLong(0)
    private val duplicateCount = AtomicLong(0)

    fun processIfNew(...): ExactlyOnceResult { /* inkrementuje odpowiedni licznik */ }
    fun counters(): ExactlyOnceCounters = ExactlyOnceCounters(processedCount.get(), duplicateCount.get())
}
```

**„Recovered"/„Failed" (61-Dashboard.md §2, 68-Statystyki.md §3) POZOSTAJĄ POZA ZAKRESEM** — SPEC-0008 ogranicza `ExactlyOnceEngine` wyłącznie do identyfikacji/wykrywania duplikatów, nie routingu/dostarczania; „odzyskane po awarii"/„nieudane" wymagałyby danych ze śledzenia `ProcessingState` w czasie (Gateway Engine), które nigdzie nie są kumulowane. Ten sam rodzaj ograniczenia co kolejki Schedulera — jawnie odnotowane, nie cicho pominięte.

**Kolejki Schedulera — jawnie odłożone.** Rozszerzenie `SchedulerProvider` o śledzenie stanu zadań (Waiting/Processing/Retrying/Failed, powiązanie z `MessagePriority`) to realne rozszerzenie kontraktu z Fazy 1 — poza zakresem tej iteracji/fazy, analogicznie do „Dodaj adapter" (Faza 5) i „Restart Gateway"/„Reload Configuration" (SA-15, ta sama Część D). Sekcja „Kolejki" ekranu Dashboard (Iteracja 6.18) jawnie renderuje komunikat o niedostępności, nie fikcyjne dane.

## Konsekwencje

- `HealthMonitor`/`ExactlyOnceEngine` pozostają w większości niezmienione (wyłącznie addytywne metody odczytu) — `GatewayEngine`/`RoutingEngine` w 100% nietknięte.
- „Recovered"/„Failed" i „Kolejki" to teraz DWA jawnie udokumentowane ograniczenia tej fazy (obok istniejących: „Dodaj adapter", CPU na Androidzie, Restart Gateway/Reload Configuration) — konsolidowane w Architectural Debt Report Fazy 6 (Iteracja 6.30).
- `GatewayInfo` wymaga konkretnej wartości `version` dostarczonej przez punkt kompozycji — dla harnessu weryfikacyjnego (Iteracja 6.29) i dla `:platform-android` osobno (rozstrzygane przy faktycznym spięciu, nie w tym ADR).

## Dokumenty powiązane

- 30-Infrastructure/35-Health-Monitor.md
- 10-Core/17-Exactly-Once.md
- 60-User-Interface/61-Dashboard.md
- 60-User-Interface/68-Statystyki.md
- 91-Specification/SPEC-0008-Exactly-Once-Contract.md
- 91-Specification/SPEC-0013-Scheduler-Provider-Contract.md
- 91-Specification/SPEC-0020-Health-Aggregation-Contract.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
