# midoMAIL 2.0

# Dokument 01 — Wizja produktu

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje długoterminową wizję produktu midoMAIL 2.0 oraz jego rolę jako uniwersalnej platformy Communication Gateway.

---

# 2. Wizja

midoMAIL jest autonomicznym silnikiem komunikacyjnym odpowiedzialnym za bezpieczne, niezawodne i kontrolowane przekazywanie komunikatów pomiędzy różnymi kanałami komunikacji. Produkt nie jest związany z żadnym konkretnym transportem, protokołem ani platformą uruchomieniową.

---

# 3. Misja

Celem projektu jest stworzenie otwartej platformy integracyjnej, która umożliwia łączenie systemów komunikacyjnych poprzez wspólny model przetwarzania komunikatów oraz stabilne kontrakty architektoniczne.

---

# 4. Tożsamość produktu

midoMAIL jest:

- Communication Gateway,
- platformą integracyjną,
- mikroserwerem biznesowym,
- silnikiem routingu komunikatów,
- fundamentem do budowy adapterów komunikacyjnych.

midoMAIL nie jest:

- aplikacją SMS,
- klientem poczty elektronicznej,
- komunikatorem,
- archiwum wiadomości,
- produktem zależnym od systemu Android.

---

# 5. Cele strategiczne

- całkowita niezależność od transportów,
- możliwość rozszerzania o nowe adaptery bez modyfikacji rdzenia,
- wsparcie dla Exactly Once Processing,
- wysoka dostępność i odporność na awarie,
- pełna obserwowalność działania Communication Gateway,
- możliwość uruchamiania na różnych platformach.

---

# 6. Zasady rozwoju

Rozwój projektu jest prowadzony zgodnie z zasadą "Core First". Najpierw rozwijany jest model domenowy i architektura rdzenia, następnie kontrakty, a dopiero później adaptery oraz infrastruktura. Żadna implementacja nie może wymuszać zmian w modelu domenowym ani architekturze Communication Gateway.

## Dokument startowy
Pełna struktura dokumentacji została opisana w `README.md` (w katalogu głównym Documentation).

---

# 7. Dokumenty powiązane

- 00-Foundation/02-Zalozenia-architektoniczne.md
- 00-Foundation/06-Glossary.md
- 50-Quality/55-Roadmap.md
- 90-ADR/ADR-0001-Communication-Gateway.md
