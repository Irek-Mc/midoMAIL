# ADR-0005 — MessagePriority w modelu domenowym

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Model GatewayMessage (10-Core/12-GatewayMessage.md, SPEC-0001-GatewayMessage.md) nie zawierał pojęcia priorytetu komunikatu — wszystkie komunikaty były traktowane równorzędnie w kolejkowaniu (Scheduler), routingu i zapytaniach Message Store. GatewayMessage jest kontraktem wersjonowanym, ale dodanie nowego, obowiązkowego pola do Identity po rozpoczęciu implementacji byłoby zmianą łamiącą kontrakt (SPEC-0001-GatewayMessage.md, §Zasady zgodności) — wymagałoby migracji już zapisanych komunikatów i rewizji każdego adaptera. Właściwy moment na tę decyzję jest przed implementacją, nie po.

Istotne rozróżnienie terminologiczne: `Priority` jest już zajętym pojęciem — to pole reguły routingu (10-Core/13-Routing.md, §Model reguł routingu; SPEC-0007-Routing-Contract.md), rozstrzygające, która reguła wygrywa przy wielu pasujących. Priorytet komunikatu jest pojęciem odrębnym i wymaga osobnej nazwy, aby uniknąć pomyłki między „priorytetem reguły" a „priorytetem wiadomości".

## Decyzja

Do modelu GatewayMessage (sekcja Identity) dodaje się pole **`MessagePriority`** — odrębne od `Priority` reguły routingu.

```kotlin
enum class MessagePriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
```

- Wartość domyślna: `NORMAL`.
- Adapter źródłowy może nadać wartość początkową przy tworzeniu komunikatu (jeśli transport źródłowy niesie taki sygnał — w praktyce rzadkie dla SMS/Email).
- Reguła routingu może opcjonalnie nadpisać `MessagePriority` poprzez akcję `SetPriority` (SPEC-0007-Routing-Contract.md, §Model reguły routingu) — wartość po ewaluacji routingu jest wartością obowiązującą dla dalszego przetwarzania (kolejkowanie, zapytania).
- `MessagePriority` wpływa na kolejność przetwarzania w Schedulerze (10-Core/16-Scheduler.md) oraz jest polem indeksowanym i filtrowalnym w Message Store (SPEC-0009-Message-Store-Contract.md).

## Konsekwencje

- GatewayMessage.Identity zyskuje nowe, obowiązkowe pole z wartością domyślną — zgodność wsteczna zachowana przez domyślną wartość `NORMAL` dla komunikatów/adapterów, które go nie ustawiają.
- Scheduler kolejkuje zadania w obrębie tego samego stanu gotowości według `MessagePriority` malejąco, a przy równym priorytecie — według kolejności przybycia (FIFO) — nie jest to osobny, równoległy tor przetwarzania ani gwarancja czasowa (SLA), wyłącznie kolejność.
- Routing Engine otrzymuje `MessagePriority` jako dodatkowy input decyzji (SPEC-0007-Routing-Contract.md, §Dane wejściowe) i może go modyfikować poprzez akcję reguły.
- Nie wprowadza się w tym etapie oddzielnych „torów" (lanes) ani limitów przepustowości per priorytet — to świadomie odłożone rozszerzenie, możliwe do dodania później bez zmiany kontraktu, ponieważ `MessagePriority` jako pole już istnieje.
- UI (60-User-Interface/61-Dashboard.md, 62-Komunikaty.md, 63-Routing.md) prezentuje i pozwala filtrować po `MessagePriority`, oraz definiować akcję `SetPriority` w regułach routingu.

## Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/13-Routing.md
- 10-Core/16-Scheduler.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0007-Routing-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
