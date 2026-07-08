# midoMAIL 2.0

# Dokument 67 — Diagnostyka

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran diagnostyki umożliwia analizę działania Communication Gateway oraz identyfikację problemów bez konieczności przeglądania surowych logów.

---

# 2. Obszary diagnostyczne

- Gateway Engine
- Routing
- Exactly Once
- Adaptery
- Scheduler
- Event Bus
- Baza danych
- Platforma

---

# 3. Widoki

### Stan komponentów
- Aktualny stan
- Ostatnia zmiana
- Czas działania

### Ślad komunikatu (Message Trace)
- ExternalReference
- MessageId
- CorrelationId
- Historia zmian Processing State
- Przebyta ścieżka routingu
- Zdarzenia domenowe

### Analiza błędów
- Przyczyna
- Skutek
- Powiązane zdarzenia
- Rekomendowane działania

---

# 4. Operacje

Wyszukiwanie odpowiada Query API Message Store (91-Specification/SPEC-0009-Message-Store-Contract.md, §Query API i §Performance targets — `findByExternalReference`/`findById` jako operacje o najniższym celowym czasie odpowiedzi).

- Wyszukiwanie po ExternalReference
- Wyszukiwanie po MessageId
- Eksport raportu diagnostycznego
- Przejście do logów

---

# 5. Wymagania dla Core

Core udostępnia pełny ślad przetwarzania komunikatu oraz dane diagnostyczne przez publiczne kontrakty. UI nie odtwarza historii na podstawie logów.

---

# 6. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/17-Exactly-Once.md
- 30-Infrastructure/36-Diagnostyka.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
