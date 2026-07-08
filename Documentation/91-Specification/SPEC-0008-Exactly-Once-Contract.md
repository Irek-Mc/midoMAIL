# SPEC-0008 — Exactly Once Contract

**Status:** Accepted
**Powiązany dokument:** 10-Core/17-Exactly-Once.md

---

# Cel

Dokument definiuje kontrakt techniczny mechanizmu Exactly Once Processing odpowiedzialnego za zapewnienie pojedynczego, spójnego przetworzenia komunikatu w granicach Communication Gateway.

---

# Założenia

- Exactly Once jest właściwością architektury, a nie transportu.
- Każdy komunikat posiada globalnie unikalny MessageId.
- Stan przetwarzania jest trwały i audytowalny.
- Mechanizm działa niezależnie od platformy i adapterów.

---

# Wymagane kontrakty

Mechanizm współpracuje z:

- GatewayMessage,
- Processing State,
- Message Store,
- Event Model,
- Portami trwałości.

---

# Minimalny model operacji

1. Rejestracja komunikatu.
2. Weryfikacja duplikatu — realizowana atomową operacją `insertIfAbsent(ExternalReference, GatewayMessage)` Message Store (91-Specification/SPEC-0009-Message-Store-Contract.md, §Atomowość i Exactly Once), nie osobnymi, nie-atomowymi krokami sprawdzenia i zapisu.
3. Rezerwacja przetwarzania.
4. Zatwierdzenie wyniku.
5. Publikacja zdarzeń końcowych.

---

# Wymagania

- deterministyczne decyzje,
- odporność na ponowienia,
- możliwość bezpiecznego wznowienia po awarii,
- pełna identyfikowalność operacji,
- zgodność z wersjonowaniem kontraktów.

---

# Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Core/17-Exactly-Once.md
- SPEC-0001-GatewayMessage.md
- SPEC-0003-Event-Model.md
- SPEC-0004-Processing-State.md
- SPEC-0002-Porty.md
- SPEC-0009-Message-Store-Contract.md

## ExternalReference
ExternalReference (SourceEventId) jest obowiązkowym elementem kontraktu. Adapter przekazuje naturalny identyfikator komunikatu źródłowego. Mechanizm Exactly Once wykorzystuje ExternalReference do wykrywania duplikatów przed utworzeniem nowego GatewayMessage.

## Ręczne ponowne przetworzenie
Operacja administracyjna „ponowne przetworzenie" (dostępna w UI, patrz 60-User-Interface/62-Komunikaty.md) nie omija Exactly Once. Jest to jawnie audytowana operacja, która świadomie unieważnia istniejący rekord deduplikacji dla danego ExternalReference i rejestruje to unieważnienie jako odrębne zdarzenie domenowe — w odróżnieniu od automatycznego ponownego przetworzenia przez adapter, które Exactly Once zawsze blokuje.
