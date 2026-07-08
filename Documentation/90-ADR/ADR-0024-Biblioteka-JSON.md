# ADR-0024 — Biblioteka JSON dla Adaptera REST

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

ADR-0017 (Faza 4) hand-rolled prosty, 5-polowy JSON dla płaskiego payloadu webhooka, jawnie odnotowując: „brak biblioteki JSON gdziekolwiek w repozytorium... rozszerzenie wymagałoby osobnego ADR". Adapter REST (Faza 5) wymaga znacznie bogatszych, zagnieżdżonych kształtów (lista adapterów z zagnieżdżonymi `Metrics`, reguły routingu z zagnieżdżonymi `RoutingConditions`, historia konfiguracji jako lista) — ręczne budowanie takiego JSON byłoby kruche i pracochłonne na tę skalę, sprzeczne z zasadą „prosty, czytelny, jednoznaczny" (50-Quality/51-Standard-kodowania.md) bardziej niż dodanie dobrze ugruntowanej biblioteki.

## Decyzja

`kotlinx.serialization` (`kotlinx-serialization-json`) — oficjalna biblioteka ekosystemu Kotlin (JetBrains), drugi świadomy wyjątek od minimalizacji zależności (po jakarta.mail, Faza 2). Ograniczona wyłącznie do `:adapter-rest` (i przyszłego `:adapter-cli`, jeśli okaże się potrzebna do formatowania wyjścia) — **nigdy w `:domain`**, który pozostaje zero-zależnościowy (Architecture Gate, niezmieniony od Fazy 1).

Wymaga wtyczki Gradle `kotlin("plugin.serialization")` (generuje kod serializatorów w czasie kompilacji) — deklarowana w korzeniu projektu (`apply false`), włączana wyłącznie w `:adapter-rest`.

## Konsekwencje

- DTO w `:adapter-rest` są adnotowane `@Serializable`, oddzielone od typów domenowych w `:domain` (nigdy `@Serializable` bezpośrednio na typach domenowych — `:domain` nie zależy od `kotlinx.serialization`, mapowanie Domain↔DTO jest jawną, ręczną warstwą, analogicznie do `EmailMessageMapper`/`GatewayMessage`↔MIME w Fazie 2).
- Ryzyko: druga zależność zewnętrzna w projekcie (po jakarta.mail) — uzasadnione, bo alternatywa (ręczne budowanie zagnieżdżonego JSON) byłaby bardziej krucha niż korzyść z minimalizacji.
- CLI (Iteracja 5.9+) może, ale nie musi, korzystać z tej samej biblioteki — jego wyjście to głównie tekst/tabela, nie JSON; decyzja odroczona do konkretnej potrzeby.

## Dokumenty powiązane

- 50-Quality/51-Standard-kodowania.md
- 90-ADR/ADR-0017-Webhook-Klient-HTTP.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
