# midoMAIL 2.0

# Dokument 00 — Wprowadzenie

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Niniejszy dokument otwiera dokumentację projektu midoMAIL 2.0 i określa jej rolę, strukturę oraz zasady wykorzystania.

Dokumentacja jest nadrzędną specyfikacją projektu. Wszystkie decyzje architektoniczne, implementacyjne i testowe muszą być z nią zgodne.

---

# 2. Czym jest midoMAIL

midoMAIL jest uniwersalnym **Communication Gateway** przeznaczonym do integracji i niezawodnego przekazywania komunikatów pomiędzy różnymi kanałami komunikacji.

Communication Gateway stanowi produkt. Adaptery, transporty oraz platformy uruchomieniowe są wymienialnymi elementami infrastruktury.

---

# 3. Zakres dokumentacji

Dokumentacja została podzielona na warstwy odpowiadające architekturze systemu:

- Foundation (00) — wizja produktu, słownik pojęć i model domenowy,
- Core (10) — rdzeń Communication Gateway,
- Adapters (20) — integracja z kanałami komunikacji,
- Infrastructure (30) — komponenty wspierające,
- Platforms (40) — środowiska uruchomieniowe,
- Quality (50) — jakość, testowanie i wdrażanie,
- User Interface (60) — funkcjonalność interfejsu administracyjnego wynikająca z kontraktów Core,
- ADR (90) — decyzje architektoniczne,
- Specification (91) — szczegółowe kontrakty techniczne,
- Review (92) — wyniki przeglądów dokumentacji.

Pełna, aktualna struktura katalogów jest utrzymywana w `README.md` (w katalogu głównym Documentation) — w razie rozbieżności rozstrzyga README.

---

# 4. Zasady

- dokumentacja jest źródłem prawdy,
- implementacja wynika z dokumentacji,
- logika biznesowa pozostaje niezależna od infrastruktury,
- każdy dokument posiada jednoznacznie określony zakres odpowiedzialności,
- zmiany architektoniczne wymagają aktualizacji dokumentacji przed implementacją.

---

# 5. Dokumenty powiązane
- 06-Glossary

Kolejne dokumenty w katalogu Foundation definiują wizję produktu, założenia architektoniczne, model domenowy oraz wymagania stanowiące fundament całego projektu.