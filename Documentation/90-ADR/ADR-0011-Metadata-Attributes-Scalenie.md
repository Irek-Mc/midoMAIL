# ADR-0011 — Scalenie Metadata i Attributes w jedno pole

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

10-Core/12-GatewayMessage.md, §3 oraz SPEC-0001-GatewayMessage.md, §Struktura logiczna wymieniały `Metadata` i `Attributes` jako dwie odrębne sekcje modelu GatewayMessage, ale żadna z nich nigdy nie doczekała się dedykowanej elaboracji (w przeciwieństwie do Identity, Source/Destination, Payload, ProcessingContext — wszystkie mają rozbudowane, dedykowane sekcje). Ujawniło się to jako luka blokująca implementację Fazy 1 (Iteracja 1d).

Sprawdzono cały korpus dokumentacji (78 dokumentów): ani `Metadata`, ani `Attributes` nie są przywoływane, wykorzystywane ani zależne od żadnego innego mechanizmu opisanego gdziekolwiek indziej (w odróżnieniu od np. `ProcessingContext`, wykorzystywanego przez Routing i Exactly Once, czy `ExternalReference`, wykorzystywanego przez Exactly Once i Message Store). Brak jakiegokolwiek konkretnego zastosowania obu pól w reszcie dokumentacji wskazuje, że utrzymywanie dwóch odrębnych, niezdefiniowanych mechanizmów byłoby zbędną abstrakcją (50-Quality/51-Standard-kodowania.md, zasada „nie twórz zbędnych abstrakcji").

## Decyzja

`Metadata` i `Attributes` zostają scalone w jedno pole: **Attributes** — generyczny, rozszerzalny bagaż par klucz-wartość (`Map<String, String>`), dołączany przez adaptery lub Gateway Engine do celów rozszerzeń, bez z góry ustalonej struktury. Nazwa `Metadata` zostaje usunięta z obu dokumentów jako duplikat tego samego pojęcia pod inną nazwą.

Wybrano nazwę `Attributes` (nie `Metadata`), ponieważ to ona posiadała już opisową adnotację „atrybuty rozszerzające" w 12-GatewayMessage.md, §3, zgodną z zamierzonym charakterem pola.

## Konsekwencje

- `GatewayMessage` posiada jedno pole `attributes: Map<String, String>` (domyślnie puste), nie dwa.
- Struktura logiczna GatewayMessage liczy odtąd 6 sekcji (Identity, Source, Destination, Payload, Attributes, ProcessingContext), nie 7.
- Jeżeli w przyszłości pojawi się konkretny, udokumentowany mechanizm wymagający osobnego pola technicznych metadanych odrębnego od rozszerzeń adapterów — wymaga to nowego ADR, nie cichego przywrócenia usuniętego rozróżnienia.

## Dokumenty powiązane

- 10-Core/12-GatewayMessage.md
- 50-Quality/51-Standard-kodowania.md
- 91-Specification/SPEC-0001-GatewayMessage.md
