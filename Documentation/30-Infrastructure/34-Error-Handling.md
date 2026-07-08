# midoMAIL 2.0

# Dokument 34 — Error Handling

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę obsługi błędów w platformie midoMAIL 2.0 oraz zasady wykrywania, klasyfikacji, propagacji i raportowania błędów w Communication Gateway.

---

# 2. Założenia architektoniczne

Obsługa błędów jest odpowiedzialnością całej platformy. Każdy komponent powinien zgłaszać błędy poprzez zdefiniowane kontrakty i nie może ukrywać błędów mających wpływ na integralność systemu.

---

# 3. Klasy błędów

Platforma rozróżnia między innymi:

- błędy domenowe,
- błędy infrastrukturalne,
- błędy adapterów,
- błędy konfiguracji,
- błędy bezpieczeństwa,
- błędy krytyczne wymagające interwencji.

---

# 4. Wymagania

System powinien zapewniać:

- jednoznaczną klasyfikację błędów,
- identyfikatory korelacyjne,
- możliwość ponawiania operacji zgodnie z polityką Retry,
- publikację zdarzeń diagnostycznych,
- bezpieczne przechodzenie do stanu awaryjnego, jeśli wymagają tego reguły architektury.

---

# 5. Integracja

Mechanizm współpracuje z Gateway Engine, Event Bus, Schedulerem, Exactly Once, systemem logowania oraz Health Monitorem.

---

# 6. Throttling nie jest błędem

Odrzucenie operacji przez Rate Limiter (91-Specification/SPEC-0011-Rate-Limiting-Contract.md) nie jest klasyfikowane jako błąd — to planowane opóźnienie o znanym czasie, obsługiwane przez Scheduler, odrębne od polityki Retry opisanej w §4. Błąd (i powiązana polityka Retry) dotyczy nieprzewidzianego niepowodzenia operacji; throttling dotyczy przewidzianego, tymczasowego braku zdolności transportu.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 11-Gateway-Engine
- 15-Event-Bus
- 16-Scheduler
- 17-Exactly-Once
- 33-Logowanie
- 35-Health-Monitor
- 90-ADR/ADR-0006-Rate-Limiting.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md