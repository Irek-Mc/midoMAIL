package midomail.domain.port

/**
 * Identyfikator zadania Schedulera (10-Core/16-Scheduler.md, §3: „każde zadanie posiada
 * jednoznaczny identyfikator i stan"; SPEC-0013-Scheduler-Provider-Contract.md).
 */
@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskId nie może być pusty" }
    }
}

/**
 * Port planowania zadań (10-Core/16-Scheduler.md; SPEC-0013-Scheduler-Provider-Contract.md).
 *
 * Wywołanie [schedule] z tym samym [TaskId] co już zaplanowane zadanie zastępuje poprzednie
 * planowanie.
 */
interface SchedulerProvider {
    fun schedule(taskId: TaskId, intervalMillis: Long, task: () -> Unit)
    fun cancel(taskId: TaskId)
}
