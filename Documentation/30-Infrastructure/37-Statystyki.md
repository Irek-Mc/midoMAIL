# midoMAIL 2.0

# Dokument 37 — Statystyki

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę systemu statystyk platformy midoMAIL 2.0. System statystyk dostarcza danych ilościowych opisujących pracę Communication Gateway oraz wspiera monitorowanie, planowanie pojemności i analizę wydajności.

---

# 2. Odpowiedzialność

System statystyk odpowiada za:

- gromadzenie metryk,
- agregację danych,
- obliczanie wskaźników wydajności,
- udostępnianie statystyk administratorom i systemom zewnętrznym,
- archiwizację danych statystycznych zgodnie z polityką retencji.

System statystyk nie realizuje logiki biznesowej i nie wpływa na przebieg przetwarzania komunikatów.

---

# 3. Zakres metryk

Przykładowe metryki obejmują:

- liczbę przetworzonych komunikatów,
- przepustowość Gateway,
- czasy przetwarzania,
- skuteczność dostarczeń,
- wykorzystanie adapterów,
- liczbę błędów i ponowień,
- dostępność komponentów.

---

# 4. Wymagania

System powinien zapewniać:

- definiowanie okresów agregacji,
- eksport metryk,
- możliwość integracji z systemami monitoringu,
- niskie obciążenie rdzenia Gateway,
- spójność danych statystycznych.

---

# 5. Integracja

System współpracuje z Event Bus, Health Monitor, Diagnostyką, Logowaniem oraz adapterami publikującymi metryki.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 15-Event-Bus
- 33-Logowanie
- 35-Health-Monitor
- 36-Diagnostyka
- 53-Dokumentacja-administratora