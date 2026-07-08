# SPEC-0004 — Processing State

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/11-Gateway-Engine.md, 10-Core/17-Exactly-Once.md

---

# Cel

Dokument definiuje model stanów przetwarzania komunikatu wykorzystywany przez Communication Gateway. Model zapewnia jednolitą interpretację cyklu życia komunikatu niezależnie od transportu i platformy.

---

# Założenia

- Stan przetwarzania jest elementem domeny.
- Zmiana stanu jest jawna i audytowalna.
- Przejścia pomiędzy stanami są deterministyczne.
- Model wspiera Exactly Once Processing.

---

# Podstawowe stany

- Accepted
- Validated
- Routed
- Scheduled
- Processing
- Delivered
- Completed
- Failed
- Cancelled

---

# Reguły przejść

- Każde przejście wymaga walidacji.
- Niedozwolone są przejścia omijające reguły domenowe.
- Zmiana stanu powinna publikować odpowiednie zdarzenie domenowe.
- Stan końcowy nie może zostać zmieniony bez rozpoczęcia nowego procesu.

---

# Wymagania

Model stanów musi umożliwiać:

- odtworzenie historii przetwarzania,
- korelację z identyfikatorami komunikatu,
- diagnostykę błędów,
- bezpieczne wznowienie przetwarzania po awarii.

---


## Rozszerzone stany dostarczenia

Model rozróżnia co najmniej:
- HandedToAdapter (przekazano do adaptera),
- Sent (adapter potwierdził wysłanie),
- Delivered (otrzymano potwierdzenie dostarczenia, jeśli transport je obsługuje).

## Throttling a stan Scheduled

Gdy Rate Limiter zwraca decyzję Throttled (SPEC-0011-Rate-Limiting-Contract.md), komunikat pozostaje w stanie `Scheduled` z zaplanowanym czasem ponowienia — throttling nie jest osobnym stanem ani błędem, wykorzystuje istniejący stan `Scheduled` z inną przyczyną planowania niż standardowy harmonogram.

---

# Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- SPEC-0001-GatewayMessage.md
- SPEC-0003-Event-Model.md
- SPEC-0011-Rate-Limiting-Contract.md
- 10-Core/17-Exactly-Once.md