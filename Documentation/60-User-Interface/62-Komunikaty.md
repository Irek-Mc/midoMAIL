# midoMAIL 2.0

# Dokument 62 — Komunikaty

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran umożliwia przegląd i analizę wszystkich komunikatów przetwarzanych przez Communication Gateway niezależnie od transportu.

---

# 2. Widok listy

Każdy rekord prezentuje:

- MessageId
- ExternalReference
- Kanał źródłowy
- Kanał docelowy
- Adapter źródłowy
- Adapter docelowy
- Processing State
- MessagePriority
- Czas utworzenia
- Ostatnia aktualizacja

---

# 3. Wyszukiwanie i filtry

Filtry i paginacja odpowiadają wprost Query API Message Store (91-Specification/SPEC-0009-Message-Store-Contract.md, §Query API) — UI nie implementuje własnej logiki wyszukiwania ponad ten kontrakt.

- MessageId
- ExternalReference
- CorrelationId
- Kanał
- Adapter
- Zakres czasu
- Status przetwarzania
- MessagePriority
- Pełnotekstowe wyszukiwanie w treści (Payload.Content)

---

# 4. Szczegóły komunikatu

- Identity
- Source
- Destination
- Payload (zgodnie z uprawnieniami)
- Historia Processing State
- Zdarzenia
- Routing
- Exactly Once

---

# 5. Operacje

Ponowne przetworzenie jest jawnie audytowaną operacją administracyjną (patrz SPEC-0008, §Ręczne ponowne przetworzenie) — świadomie unieważnia rekord deduplikacji dla danego ExternalReference i nie jest automatycznym ponowieniem, które Exactly Once zawsze blokuje.

- Otwórz ślad komunikatu
- Eksport danych
- Ponowne przetworzenie (jeżeli dopuszcza polityka)
- Przejście do diagnostyki

---

# 6. Wymagania dla Core

Core udostępnia kompletny model komunikatu wraz z historią przetwarzania poprzez publiczne kontrakty. UI nie rekonstruuje informacji z logów ani adapterów.

---

# 7. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/17-Exactly-Once.md
- 90-ADR/ADR-0005-Message-Priority.md
- 91-Specification/SPEC-0008-Exactly-Once-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
