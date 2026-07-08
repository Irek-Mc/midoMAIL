# SPEC-0013 — Scheduler Provider Contract

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/16-Scheduler.md, 10-Core/18-Porty.md, SPEC-0002-Porty.md

---

# Cel

Dokument definiuje techniczną sygnaturę portu `SchedulerProvider` — dotąd odłożoną przez SPEC-0002-Porty.md, §Plan dalszej specyfikacji. Ujawniło się to jako luka blokująca implementację Fazy 1 (Iteracja 4b): 10-Core/16-Scheduler.md, §3 wprost wymaga „każde zadanie posiada jednoznaczny identyfikator i stan", ale żadna sygnatura metody nie została nigdzie podana.

---

# Interfejs

```kotlin
@JvmInline
value class TaskId(val value: String)

interface SchedulerProvider {
    fun schedule(taskId: TaskId, intervalMillis: Long, task: () -> Unit)
    fun cancel(taskId: TaskId)
}
```

`schedule` planuje zadanie cykliczne identyfikowane przez `taskId` (10-Core/16-Scheduler.md, §3) — wywołanie z tym samym `taskId` co już zaplanowane zadanie zastępuje poprzednie planowanie. `cancel` usuwa zaplanowane zadanie.

---

# Zakres w Fazie 1

Pełny cykl życia zadania (Scheduled, Waiting, Running, Completed, Retrying, Failed, Cancelled — 10-Core/16-Scheduler.md, §4) oraz możliwość odpytania o bieżący stan konkretnego zadania **nie są częścią minimalnego kontraktu Fazy 1** — port zapewnia jedynie identyfikowalność (`TaskId`) wymaganą przez założenie architektoniczne, bez pełnego query stanu. Rozszerzenie o odpytywanie stanu zadania jest dodawane w fazie, w której pojawi się konkretny konsument tej funkcji (np. UI Dashboard, Faza 6) — wymaga wtedy nowego ADR.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/16-Scheduler.md
- 10-Core/18-Porty.md
- 91-Specification/SPEC-0002-Porty.md
