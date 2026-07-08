# midoMAIL 2.0

# Dokument 14 — Registry Adapterow

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę komponentu Registry Adapterów odpowiedzialnego za rejestrację, udostępnianie oraz zarządzanie cyklem życia adapterów komunikacyjnych.

---

# 2. Odpowiedzialność

Registry Adapterów odpowiada za:

- rejestrację adapterów,
- identyfikację ich możliwości,
- udostępnianie adapterów innym komponentom poprzez kontrakty,
- monitorowanie stanu adapterów,
- bezpieczne uruchamianie i zatrzymywanie adapterów.

Registry nie realizuje routingu, nie przetwarza komunikatów i nie zawiera logiki biznesowej.

---

# 3. Założenia architektoniczne

- Adapter identyfikowany jest jednoznacznym identyfikatorem.
- Każdy adapter deklaruje obsługiwane kanały i możliwości.
- Rejestr nie zna implementacji transportów.
- Dostęp do adapterów odbywa się wyłącznie przez kontrakty.

---

# 4. Cykl życia adaptera

Stany adaptera:

- Registered,
- Initializing,
- Ready,
- Busy,
- Degraded,
- Stopping,
- Stopped,
- Failed.

Przejścia pomiędzy stanami są publikowane jako zdarzenia domenowe. Pełna tabela dozwolonych przejść: 91-Specification/SPEC-0014-Adapter-Lifecycle-Contract.md.

---

# 5. Współpraca z innymi komponentami

Registry współpracuje z:

- Gateway Engine,
- Routing Engine,
- Health Monitor,
- Plugin SDK,
- Scheduler.

---

# 6. Rozszerzalność

Dodanie nowego adaptera wymaga jedynie implementacji odpowiednich kontraktów i rejestracji w Registry. Nie wymaga zmian w Gateway Engine ani Routing Engine.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 11-Gateway-Engine
- 13-Routing
- 18-Porty
- 19-Plugin-SDK
- 90-ADR/ADR-0014-Registry-Start-Failure.md
- 90-ADR/ADR-0015-Rejestracja-Wyslanego-Message-Id.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
- 91-Specification/SPEC-0014-Adapter-Lifecycle-Contract.md
