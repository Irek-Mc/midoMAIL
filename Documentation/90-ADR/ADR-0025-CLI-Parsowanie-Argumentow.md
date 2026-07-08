# ADR-0025 — CLI: parsowanie argumentów

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Adapter CLI (`20-Adapters/25-Adapter-CLI.md`) potrzebuje mechanizmu dyspozycji `args: Array<String>` na konkretne operacje administracyjne (SPEC-0024). Rozważane opcje: (1) zwykłe `Array<String>` z ręcznym dyspozytorem komenda→handler. (2) Biblioteka (np. clikt) — wygodniejsza obsługa flag/pomocy/walidacji argumentów, ale nowa zależność dla ograniczonego, znanego z góry zbioru komend (4 kategorie × kilka operacji każda, ustalone w SPEC-0024).

## Decyzja

Zwykłe `args: Array<String>`, dyspozytor komenda→handler zbudowany ręcznie w `:adapter-cli` — zgodne z minimalizacją zależności (50-Quality/51-Standard-kodowania.md) dla zbioru komend znanego i ograniczonego z góry (w przeciwieństwie do JSON w Adapterze REST, gdzie kształty są zagnieżdżone i liczne — uzasadniające ADR-0024 — tu struktura jest płaska: `<kategoria> <operacja> [argumenty]`).

```kotlin
interface CliCommand {
    val name: String
    fun execute(args: List<String>): String
}

class CliDispatcher(private val commands: List<CliCommand>) {
    fun dispatch(args: Array<String>): String
}
```

Format komendy: `<nazwa>` (pierwszy argument) + pozostałe argumenty przekazane do `execute()`. Nieznana komenda / brak komendy zwraca czytelny komunikat z listą dostępnych komend, nie wyjątek.

## Konsekwencje

- Zero nowych zależności Gradle.
- Brak automatycznej walidacji typów/flag (np. `--port=8080`) — każda komenda parsuje własne argumenty ręcznie; wystarczające dla ustalonego z góry, niewielkiego zbioru operacji administracyjnych.
- Format wyjścia (tekst zwykły) ustalany per-komenda w Iteracjach 5.14-5.15, nie w tym dokumencie.

## Dokumenty powiązane

- 20-Adapters/25-Adapter-CLI.md
- 50-Quality/51-Standard-kodowania.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
