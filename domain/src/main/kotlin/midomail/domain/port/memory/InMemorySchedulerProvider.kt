package midomail.domain.port.memory

import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Referencyjna implementacja [SchedulerProvider] oparta o `ScheduledExecutorService`
 * (10-Core/16-Scheduler.md; SPEC-0013-Scheduler-Provider-Contract.md).
 *
 * Wywołanie [schedule] z tym samym [TaskId] co już zaplanowane zadanie zastępuje poprzednie
 * planowanie (SPEC-0013).
 */
class InMemorySchedulerProvider : SchedulerProvider {

    private val executor = Executors.newScheduledThreadPool(1)
    private val scheduledTasks = ConcurrentHashMap<TaskId, ScheduledFuture<*>>()

    override fun schedule(taskId: TaskId, intervalMillis: Long, task: () -> Unit) {
        scheduledTasks.remove(taskId)?.cancel(false)
        val future = executor.scheduleAtFixedRate(task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
        scheduledTasks[taskId] = future
    }

    override fun cancel(taskId: TaskId) {
        scheduledTasks.remove(taskId)?.cancel(false)
    }
}
