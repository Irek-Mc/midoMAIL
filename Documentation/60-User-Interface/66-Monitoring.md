# midoMAIL 2.0

# Dokument 66 — Monitoring

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran zapewnia bieżący podgląd kondycji Communication Gateway oraz wszystkich jego komponentów.

---

# 2. Widoki

- Stan Gateway
- Health komponentów
- Dostępność adapterów
- Kolejki przetwarzania
- Wydajność (CPU, RAM, Storage, Network)
- Aktywne alerty

---

# 3. Alerty

Każdy alert (00-Foundation/06-Glossary.md, hasło „Alert") zawiera:

- poziom (Info, Warning, Error, Critical),
- źródło,
- czas wystąpienia,
- status,
- zalecane działania.

---

# 4. Operacje

Brak potwierdzenia alertu w skonfigurowanym czasie uruchamia eskalację (30-Infrastructure/38-Powiadomienia.md, §5) — kolejne powiadomienie i/lub dodatkowy kanał, aż do potwierdzenia.

- Potwierdzenie alertu
- Przejście do diagnostyki
- Przejście do logów
- Eksport raportu

---

# 5. Wymagania dla Core

Core udostępnia ustandaryzowany model Health, metryki, aktywne alerty oraz historię zmian stanu poprzez publiczne kontrakty. UI nie interpretuje stanu komponentów samodzielnie.

---

# 6. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/35-Health-Monitor.md
- 30-Infrastructure/38-Powiadomienia.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
