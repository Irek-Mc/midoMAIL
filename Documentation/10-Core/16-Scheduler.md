# midoMAIL 2.0

# Dokument 16 — Scheduler

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę komponentu Scheduler odpowiedzialnego za planowanie i wykonywanie zadań czasowych oraz odroczonych w platformie midoMAIL 2.0.

---

# 2. Odpowiedzialność

Scheduler odpowiada za:

- planowanie zadań jednorazowych i cyklicznych,
- wykonywanie zadań zgodnie z harmonogramem,
- zarządzanie kolejką zadań,
- ponawianie zadań zgodnie z polityką Retry,
- współpracę z Gateway Engine oraz Exactly Once Engine.

Scheduler nie zawiera logiki biznesowej i nie podejmuje decyzji dotyczących routingu komunikatów.

Port `SchedulerProvider` (minimalna sygnatura dla Fazy 1 — planowanie identyfikowane przez `TaskId`, zgodnie z §3): 91-Specification/SPEC-0013-Scheduler-Provider-Contract.md.

---

# 3. Założenia architektoniczne

- planowanie jest niezależne od platformy uruchomieniowej,
- wykonywanie zadań odbywa się poprzez kontrakty,
- harmonogram może być odtworzony po restarcie systemu,
- polityki planowania są konfigurowalne,
- każde zadanie posiada jednoznaczny identyfikator i stan.

---

# 4. Cykl życia zadania

Typowy cykl życia obejmuje stany:

- Scheduled,
- Waiting,
- Running,
- Completed,
- Retrying,
- Failed,
- Cancelled.

Zmiany stanu publikowane są jako zdarzenia domenowe.

---

# 5. Kolejkowanie według MessagePriority

W obrębie stanu Waiting zadania powiązane z komunikatem są uszeregowane malejąco według `MessagePriority` komunikatu (10-Core/12-GatewayMessage.md, §MessagePriority; ADR-0005-Message-Priority.md), a przy równym priorytecie — według kolejności przybycia (FIFO). Nie jest to osobny tor przetwarzania ani gwarancja czasowa (SLA) — wyłącznie kolejność wykonania w obrębie tej samej kolejki.

---

# 6. Integracja

Scheduler współpracuje z:

- Gateway Engine,
- Event Bus,
- Exactly Once Engine,
- Health Monitor,
- komponentami infrastrukturalnymi odpowiedzialnymi za wykonanie zadań.

---

# 7. Rozszerzalność

Nowe strategie planowania, retry i kolejkowania powinny być dodawane poprzez kontrakty bez modyfikacji rdzenia Scheduler.

---

# 8. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 11-Gateway-Engine
- 15-Event-Bus
- 17-Exactly-Once
- 18-Porty
- 35-Health-Monitor
- 90-ADR/ADR-0005-Message-Priority.md
- 91-Specification/SPEC-0013-Scheduler-Provider-Contract.md
