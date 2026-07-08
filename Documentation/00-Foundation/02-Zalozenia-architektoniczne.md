# midoMAIL 2.0

# Dokument 02 — Założenia architektoniczne

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje fundamentalne założenia architektoniczne platformy midoMAIL 2.0. Stanowi podstawę wszystkich decyzji projektowych, implementacyjnych oraz rozwoju systemu.

---

# 2. Zasady nadrzędne

- Communication Gateway jest jedynym miejscem realizacji logiki biznesowej.
- Rdzeń systemu pozostaje całkowicie niezależny od transportów i platform uruchomieniowych.
- Wszystkie zależności architektoniczne są skierowane do wnętrza systemu zgodnie z Clean Architecture.
- Adaptery komunikacyjne odpowiadają wyłącznie za integrację z kanałami zewnętrznymi.
- Infrastruktura nie może wpływać na model domenowy ani reguły biznesowe.

---

# 3. Styl architektoniczny

Projekt opiera się na zasadach Clean Architecture oraz Ports & Adapters. Komunikacja pomiędzy komponentami odbywa się wyłącznie poprzez jawnie zdefiniowane kontrakty. Każdy komponent posiada jednoznacznie określoną odpowiedzialność.

---

# 4. Niezależność od transportów

Communication Gateway nie zakłada istnienia żadnego konkretnego kanału komunikacyjnego. SMS, E-mail, REST, WebSocket i przyszłe transporty są równorzędnymi adapterami i nie wpływają na architekturę rdzenia.

---

# 5. Rozszerzalność

Dodanie nowego adaptera, transportu lub platformy nie może wymagać modyfikacji Communication Gateway Engine ani modelu domenowego. Rozszerzenia są realizowane poprzez stabilne kontrakty oraz Plugin SDK.

---

# 6. Niezawodność

Architektura musi zapewniać odporność na awarie, możliwość automatycznego odzyskiwania sprawności oraz wspierać Exactly Once Processing jako wymaganie architektoniczne.

---

# 7. Obserwowalność

Każdy komponent powinien udostępniać zdarzenia, metryki oraz informacje diagnostyczne umożliwiające monitorowanie i analizę działania całego Communication Gateway.

---

# 8. Zasada dokument-first

Zmiany architektoniczne są najpierw opisywane i zatwierdzane w dokumentacji. Implementacja jest realizacją zaakceptowanej dokumentacji i nie stanowi źródła decyzji projektowych.

# Zasady spójności architektury

## Jedno źródło prawdy
Każda informacja architektoniczna posiada jedno miejsce definicji. Pozostałe dokumenty odwołują się do niej zamiast powielać treść.

## Podział odpowiedzialności dokumentacji
- 00-Foundation — wizja, pojęcia i zasady.
- 10-Core — architektura logiczna i modele domenowe.
- 20-Adapters — kontrakty i zachowanie adapterów.
- 30-Infrastructure — usługi techniczne i operacyjne.
- 40-Platforms — wymagania platformowe.
- 50-Quality — standardy jakości i procesu.
- 60-User-Interface — funkcjonalność interfejsu wynikająca z kontraktów Core.
- 90-ADR — decyzje architektoniczne.
- 91-Specification — szczegóły kontraktów technicznych bez powielania architektury.

---

# Dokumenty powiązane

- 00-Foundation/01-Wizja-produktu.md
- 00-Foundation/06-Glossary.md
- 10-Core/10-Architektura-systemu.md
- README.md
