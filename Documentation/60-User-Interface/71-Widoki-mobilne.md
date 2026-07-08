# midoMAIL 2.0

# Dokument 71 — Widoki mobilne

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Widoki mobilne zapewniają dostęp do najważniejszych funkcji administracyjnych bez konieczności korzystania z pełnego panelu.

---

# 2. Zakres

- Status Gateway
- Alerty
- Adaptery
- Ostatnie błędy
- Podstawowe statystyki

---

# 3. Operacje

- Potwierdzanie alertów
- Restart adaptera
- Test połączenia adaptera
- Przejście do diagnostyki komunikatu

---

# 4. Ograniczenia

Zaawansowana konfiguracja i edycja routingu są dostępne wyłącznie w pełnym interfejsie administracyjnym.

---

# 5. Wymagania dla Core

Widoki mobilne korzystają z tych samych kontraktów API co interfejs administracyjny (Adapter REST/CLI, ADR-0002). Nie istnieje odrębna logika biznesowa dla urządzeń mobilnych.

---

# 6. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 40-Platforms/40-Android.md
- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md
