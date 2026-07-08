# ADR-0020 — ConfigurationProvider: zapis, historia, rollback

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`ConfigurationProvider` (SPEC-0012-Configuration-Provider-Contract.md) jest od Fazy 1 wyłącznie do odczytu (`getValue(key): String?`), jednoklucza. SPEC-0012, §Zasady zgodności, wprost zastrzega: „rozszerzenie o w pełni typowany dostęp do konfiguracji... wymaga nowego ADR" — ten dokument jest tym ADR.

60-User-Interface/65-Konfiguracja.md §3 wymaga „Walidacja przed zapisaniem, Test konfiguracji, Podgląd zmian, Import/Eksport (YAML), Historia zmian, Przywrócenie poprzedniej wersji" — pełny zakres. §5 wymaga „Core udostępnia wersjonowaną konfigurację, walidację, historię zmian oraz możliwość bezpiecznego przeładowania bez restartu".

Zbudowanie tego w pełni (parser plików YAML zgodny z pełnym schematem SPEC-0005, import/eksport, walidacja krzyżowa między sekcjami — np. „adapter Email wymaga kompletnej konfiguracji SMTP/IMAP", „retencja deduplikacji musi być ≥ retencja treści") jest dużym, samodzielnym fragmentem pracy, wykraczającym poza to, czego potrzebuje Faza 5 do spełnienia dosłownego kryterium wyjścia (Adapter REST/CLI udostępniające operację „konfiguracja" — niekoniecznie w PEŁNYM zakresie 65-Konfiguracja.md już teraz).

## Decyzja

`ConfigurationProvider` zyskuje wąski zapis + historię w pamięci:

```kotlin
interface ConfigurationProvider {
    fun getValue(key: String): String?
    fun setValue(key: String, value: String)
    fun history(key: String): List<String>
    fun rollback(key: String): String?
}
```

`setValue` zapisuje POPRZEDNIĄ wartość klucza do historii przed nadpisaniem (jeśli klucz miał już jakąś wartość). `history(key)` zwraca poprzednie wartości, najnowsza pierwsza. `rollback(key)` przywraca najnowszą wartość z historii jako bieżącą (zdejmując ją z historii) i ją zwraca — `null`, jeśli historia jest pusta (nic do przywrócenia).

Jedyny obecny implementator (`InMemoryConfigurationProvider`) jest jedynym w całym repozytorium — rozszerzenie interfejsu o nowe metody abstrakcyjne jest bezpieczne (brak innych implementatorów do złamania).

**Świadomie POZA zakresem tej decyzji** (odnotowane jako dług architektoniczny, nie cicho pominięte):
- Parser plików YAML zgodny z pełnym schematem SPEC-0005.
- Import/Eksport (format pliku).
- Walidacja krzyżowa między sekcjami (65-Konfiguracja.md §3, SPEC-0005 §Walidacja krzyżowa).
- Bezpieczne przeładowanie bez restartu (65-Konfiguracja.md §5) — Faza 5 operuje na `ConfigurationProvider` w pamięci procesu, nie na plikach na dysku.

## Konsekwencje

- Operacja „konfiguracja" w Adapter REST/CLI (Faza 5) zarządza WYŁĄCZNIE parami klucz-wartość w pamięci procesu, z historią/rollback — nie plikami YAML na dysku.
- Pełny zakres 65-Konfiguracja.md §3/§5 pozostaje otwartym elementem Architectural Debt Report tej fazy, do rozważenia w przyszłej fazie/iteracji, gdy pojawi się konkretna potrzeba (np. Faza 6 UI rzeczywiście potrzebująca importu/eksportu plików).
- `history`/`rollback` są nieograniczone rozmiarem (in-memory, nietrwałe między restartami procesu — zgodne z ograniczeniem odziedziczonym z Faz 1-2, brak trwałego magazynu).

## Dokumenty powiązane

- 60-User-Interface/65-Konfiguracja.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0012-Configuration-Provider-Contract.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
