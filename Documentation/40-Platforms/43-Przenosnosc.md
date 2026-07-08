# midoMAIL 2.0

# Dokument 43 — Przenośność

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje wymagania dotyczące przenośności platformy midoMAIL 2.0 oraz zasady umożliwiające uruchamianie Communication Gateway na różnych platformach bez zmian w rdzeniu systemu.

---

# 2. Założenia architektoniczne

Przenośność jest jedną z podstawowych cech architektury. Model domenowy, Gateway Engine oraz pozostałe komponenty Core nie mogą zależeć od konkretnego systemu operacyjnego, frameworka ani technologii infrastrukturalnej.

---

# 3. Zasady projektowe

- wszystkie zależności od platform znajdują się poza Core,
- komunikacja z platformą odbywa się wyłącznie przez porty,
- adaptery izolują szczegóły technologiczne,
- konfiguracja jest niezależna od środowiska uruchomieniowego,
- implementacje platform mogą być wymieniane bez wpływu na domenę.

---

# 4. Wspierane środowiska

Architektura przewiduje możliwość uruchomienia między innymi na:

- Android,
- JVM,
- Linux,
- kontenerach,
- przyszłych platformach zgodnych z kontraktami architektury.

---

# 5. Wymagania

Platforma powinna zapewniać:

- identyczny model domenowy na wszystkich platformach,
- spójne kontrakty portów,
- możliwość współdzielenia kodu Core,
- niezależne implementacje warstwy infrastrukturalnej,
- jednolite testy architektoniczne dla wszystkich środowisk.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 18-Porty
- 40-Android
- 41-JVM
- 42-Linux
- 50-Testy
- 52-Deployment