# midoMAIL 2.0

# Dokument 68 — Statystyki

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran prezentuje metryki operacyjne i biznesowe Communication Gateway w ujęciu bieżącym oraz historycznym.

---

# 2. Sekcje

- Wolumen komunikatów
- Przepustowość
- Exactly Once
- Adaptery
- Routing
- Błędy
- Wydajność

---

# 3. Przykładowe metryki

### Komunikaty
- Odebrane
- Przetworzone
- Dostarczone
- Odrzucone

### Exactly Once
- Zapobieżone duplikaty
- Ponowienia
- Odzyskane po awarii

### Adaptery
- Komunikaty na adapter
- Średni czas obsługi
- Wskaźnik błędów

---

# 4. Operacje

- Wybór zakresu czasu
- Filtrowanie według adaptera i kanału
- Eksport raportów

---

# 5. Wymagania dla Core

Core udostępnia metryki jako ustandaryzowane kontrakty z możliwością agregacji czasowej. UI nie wylicza statystyk lokalnie. Zagregowane metryki przeżywają purge danych źródłowych w Message Store (91-Specification/SPEC-0009-Message-Store-Contract.md, §Retention policy) — statystyki historyczne pozostają dostępne nawet po usunięciu surowych komunikatów zgodnie z polityką retencji.

---

# 6. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/35-Health-Monitor.md
- 30-Infrastructure/37-Statystyki.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
