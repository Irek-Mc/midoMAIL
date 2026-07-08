# midoMAIL 2.0

# Dokument 15 — Event Bus

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę komponentu Event Bus odpowiedzialnego za asynchroniczną komunikację pomiędzy komponentami Communication Gateway.

---

# 2. Odpowiedzialność

Event Bus odpowiada za publikowanie, dystrybucję oraz dostarczanie zdarzeń pomiędzy komponentami platformy przy zachowaniu niskiego sprzężenia pomiędzy nimi.

Event Bus nie zawiera logiki biznesowej i nie podejmuje decyzji dotyczących przetwarzania komunikatów.

---

# 3. Założenia architektoniczne

- komunikacja odbywa się poprzez zdarzenia domenowe,
- nadawca nie zna odbiorców zdarzenia,
- odbiorcy nie wpływają na publikację zdarzenia,
- zdarzenia są niemutowalne,
- kontrakty zdarzeń są wersjonowane.

---

# 4. Zakres zastosowania

Event Bus wykorzystywany jest do:

- publikowania zdarzeń domenowych,
- powiadamiania o zmianach stanu komponentów,
- integracji komponentów Core,
- monitorowania działania Gateway,
- diagnostyki i obserwowalności.

---

# 5. Ograniczenia

Event Bus:

- nie zastępuje mechanizmu routingu,
- nie odpowiada za Exactly Once Processing,
- nie przechowuje trwałego stanu komunikatów,
- nie wykonuje operacji specyficznych dla adapterów.

---

# 6. Współpraca z komponentami

Event Bus współpracuje z Gateway Engine, Routing Engine, Schedulerem, Health Monitorem, Registry Adapterów oraz pozostałymi komponentami publikującymi lub subskrybującymi zdarzenia.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 11-Gateway-Engine
- 13-Routing
- 16-Scheduler
- 17-Exactly-Once
- 35-Health-Monitor
