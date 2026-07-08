# ADR-0010 — Model Channel (Source/Destination)

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

10-Core/12-GatewayMessage.md, §7 i SPEC-0001-GatewayMessage.md, §Source/§Destination opisywały `Source`/`Destination` jako „logiczny kanał komunikacji", bez precyzowania konkretnych pól. Ujawniło się to jako luka blokująca podczas implementacji Fazy 1 (Iteracja 1b, zgodnie z planem `/home/ibox/.claude/plans/optimized-whistling-lemur.md`): bez sposobu przeniesienia konkretnego adresu transportowego (numer telefonu, adres e-mail) przez model domenowy, żaden adapter wysyłający nie wiedziałby, dokąd faktycznie dostarczyć wiadomość — fizyczne dostarczenie nie mogłoby zajść.

## Decyzja

`Channel` składa się z:

- **ChannelType** — identyfikator logicznego typu kanału (np. `gsm`, `email`, `rest`, `websocket`, `cli`). To jedyne pole, na podstawie którego Routing Engine podejmuje decyzje (warunki reguł typu `sourceChannel: "gsm"`, `targetChannel: "email"` — SPEC-0005-Configuration-Model.md, §Przykładowy plik konfiguracyjny).
- **address** (opcjonalny `String?`) — konkretny adres transportowy (numer telefonu, adres e-mail, URL webhooka itd.), zależny od `ChannelType`. Pole **nieinterpretowane przez Core** — Gateway Engine i Routing Engine nigdy nie odczytują ani nie walidują jego wartości, wyłącznie je przenoszą. Może być puste (np. zanim Destination zostanie wyznaczone przez routing).

Zgodne z istniejącą zasadą (12-GatewayMessage.md, §7): „Gateway nie podejmuje decyzji na podstawie adresów, numerów telefonów ani adresów e-mail. Decyzje wynikają wyłącznie z jawnie zdefiniowanych metadanych modelu" — tą metadaną jest `ChannelType`, nigdy `address`.

## Konsekwencje

- Adapter mapuje `address` na format wymagany przez własny transport (numer E.164 dla GSM, adres RFC 5322 dla Email itd.) — Core nie waliduje formatu ani nie zna jego semantyki.
- `address` nigdy nie jest i nie może być warunkiem reguły routingu — tylko `ChannelType` może nim być (10-Core/13-Routing.md, SPEC-0007-Routing-Contract.md).
- `address` jest opcjonalny na poziomie typu — komunikat może istnieć z ustalonym `ChannelType` i jeszcze nieustalonym `address` w trakcie przetwarzania.

## Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/13-Routing.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0007-Routing-Contract.md
