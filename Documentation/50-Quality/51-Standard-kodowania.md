# midoMAIL 2.0

# Dokument 51 — Standard kodowania

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje standardy kodowania obowiązujące podczas rozwoju platformy midoMAIL 2.0. Celem jest zapewnienie spójności, czytelności i długoterminowej utrzymywalności kodu źródłowego.

---

# 2. Zasady ogólne

- implementacja wynika z zaakceptowanej dokumentacji,
- logika biznesowa znajduje się wyłącznie w Core,
- kod powinien być prosty, czytelny i jednoznaczny,
- preferowane jest kompozycja zamiast dziedziczenia,
- zależności są odwracane zgodnie z Clean Architecture.

---

# 3. Organizacja kodu

- pojedyncza odpowiedzialność komponentów,
- stabilne kontrakty,
- brak zależności domeny od infrastruktury,
- wydzielanie kodu platformowego do odpowiednich warstw,
- spójna struktura pakietów i modułów.

---

# 4. Jakość

Każda zmiana powinna:

- posiadać odpowiednie testy,
- zachowywać zgodność architektoniczną,
- nie naruszać kontraktów publicznych,
- być udokumentowana w razie zmian architektonicznych.

---


# 5. Zasady projektowe

- Zależności są przekazywane przez konstruktor.
- Architektura nie wymaga frameworków Dependency Injection.
- Interfejsy platformowe są izolowane za pomocą portów i adapterów.

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 02-Zalozenia-architektoniczne
- 10-Architektura-systemu
- 50-Testy
- 52-Deployment