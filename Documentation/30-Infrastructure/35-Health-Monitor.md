# midoMAIL 2.0

# Dokument 35 — Health Monitor

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę komponentu Health Monitor odpowiedzialnego za monitorowanie kondycji platformy midoMAIL 2.0 oraz ocenę dostępności i sprawności komponentów Communication Gateway.

---

# 2. Odpowiedzialność

Health Monitor odpowiada za:

- monitorowanie stanu komponentów,
- wykrywanie degradacji usług,
- ocenę gotowości i dostępności platformy,
- publikowanie informacji o stanie systemu,
- współpracę z diagnostyką i mechanizmami administracyjnymi.

Health Monitor nie realizuje logiki biznesowej ani nie wykonuje działań routingowych.

---

# 3. Założenia architektoniczne

- monitorowanie odbywa się poprzez zdefiniowane kontrakty,
- każdy komponent publikuje własny stan,
- ocena stanu jest niezależna od platformy uruchomieniowej,
- informacje o stanie są udostępniane w ujednoliconym formacie.

---

# 4. Zakres monitorowania

Monitorowane są między innymi:

- Gateway Engine,
- Registry Adapterów,
- adaptery komunikacyjne,
- Scheduler,
- Event Bus,
- warstwa trwałości,
- konfiguracja,
- zasoby platformy.

---

# 5. Wymagania

System powinien umożliwiać:

- okresowe sprawdzanie stanu,
- definiowanie progów ostrzegawczych,
- publikację zdarzeń o zmianie stanu,
- integrację z systemami monitoringu,
- raportowanie historii zmian stanu.

---

# 6. Model alertów

Health Monitor publikuje alerty o poziomach: Info, Warning, Error i Critical. Alert posiada identyfikator, źródło, czas wystąpienia, status oraz zalecane działania. Dostarczanie Alertu do administratora poza interfejsem Gateway (e-mail/push/webhook), routing według poziomu oraz eskalacja przy braku potwierdzenia są zdefiniowane w 30-Infrastructure/38-Powiadomienia.md — Health Monitor jedynie generuje Alert, nie odpowiada za jego dostarczenie.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 15-Event-Bus
- 16-Scheduler
- 30-Konfiguracja
- 33-Logowanie
- 34-Error-Handling
- 36-Diagnostyka
- 38-Powiadomienia
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
