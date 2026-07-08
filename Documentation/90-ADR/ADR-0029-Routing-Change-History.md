# ADR-0029 — Historia zmian reguł routingu

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/63-Routing.md` §3 wymaga operacji „Historia zmian", §5: „Każda zmiana reguł jest wersjonowana i audytowana." `RoutingRuleAdministration` (Faza 5, ADR-0021) inkrementuje `RuleVersion` przy `update()`, ale nie przechowuje żadnego logu poprzednich zmian — po nadpisaniu reguły poprzednia wersja jest bezpowrotnie utracona (poza samym numerem wersji).

## Decyzja

Amendment `RoutingRuleAdministration` — wewnętrzny, chronologiczny log zmian, dołączany przy każdej operacji zapisu:

```kotlin
enum class RoutingRuleChangeType { ADDED, UPDATED, REMOVED }

data class RoutingRuleChange(
    val ruleId: RuleId,
    val changeType: RoutingRuleChangeType,
    val version: RuleVersion?,
    val timestamp: Instant
)
```

`RoutingRuleAdministration.history(): List<RoutingRuleChange>` zwraca migawkę logu w kolejności chronologicznej (najstarsze pierwsze — zgodnie z konwencją `EventStore`/`InMemoryConfigurationProvider.history()`, gdzie kolejność jest jawnie udokumentowana). `version` jest `null` dla `REMOVED` (usunięta reguła nie ma już wersji do zaraportowania — ostatnia znana wersja pozostaje w poprzednim wpisie `UPDATED`/`ADDED` dla tego `ruleId`).

**Pole „kto"** — świadomie POZA zakresem tej iteracji. Pełen model RBAC (Konta/Role) nie istnieje jeszcze (ADR-0030, Iteracja 6.5, ta sama Część A) — do czasu jego zbudowania log zmian identyfikuje wyłącznie CO/KIEDY, nie KTO. Warstwa Admin API (Iteracja 6.12) łączy to z audytem `EventCategory.ADMINISTRATIVE` (Faza 5), gdzie identyfikacja wywołującego (klucz API/konto) jest już częściowo dostępna przez sam fakt uwierzytelnienia — pełne powiązanie „kto" z konkretnym wpisem historii routingu odkładane do czasu, gdy Iteracja 6.15 (RBAC w Admin API) dostarczy tożsamość konta.

## Konsekwencje

- Reguły przekazane w konstruktorze (`initialRules`) NIE są logowane jako wpisy `ADDED` — log obejmuje wyłącznie zmiany wykonane PRZEZ operacje administracyjne po utworzeniu instancji, nie stan początkowy wczytany przez punkt kompozycji (analogicznie do tego, że `ConfigurationProvider.history()` z Fazy 5 nie loguje wartości domyślnych, tylko rzeczywiste `setValue()`).
- Log rośnie nieograniczenie w pamięci przez cały czas życia procesu — dopuszczalne dla tej fazy (ten sam profil ograniczenia co historia konfiguracji w pamięci, ADR-0020, Faza 5); trwałość między restartami i limity retencji pozostają poza zakresem, tak jak inne mechanizmy „w pamięci" tego projektu.
- `RoutingEngine` pozostaje całkowicie nietknięty — log jest wyłącznie odpowiedzialnością `RoutingRuleAdministration`.
- Historia obejmuje WSZYSTKIE zmiany od uruchomienia procesu — nie tylko dla wciąż istniejących reguł (`REMOVED` pozostaje w logu, mimo że reguła zniknęła z `list()`).

## Dokumenty powiązane

- 60-User-Interface/63-Routing.md
- 90-ADR/ADR-0021-Administracja-Regul-Routingu.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
