package midomail.domain.port.memory

import midomail.domain.port.TaskId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt SchedulerProvider z SPEC-0013-Scheduler-Provider-Contract.md przez
 * referencyjną implementację opartą o rzeczywiste wykonanie (ScheduledExecutorService).
 */
class InMemorySchedulerProviderTest {

    @Test
    fun `scheduled task actually executes at the configured interval`() {
        val scheduler = InMemorySchedulerProvider()
        val executionCount = AtomicInteger(0)
        val latch = CountDownLatch(2)

        scheduler.schedule(TaskId("task-1"), intervalMillis = 20) {
            executionCount.incrementAndGet()
            latch.countDown()
        }

        val completedInTime = latch.await(2, TimeUnit.SECONDS)

        assertTrue(completedInTime, "Zadanie powinno wykonać się co najmniej dwa razy w ciągu 2 sekund")
        assertTrue(executionCount.get() >= 2)

        scheduler.cancel(TaskId("task-1"))
    }

    @Test
    fun `cancel stops further executions`() {
        val scheduler = InMemorySchedulerProvider()
        val executionCount = AtomicInteger(0)
        val firstExecutionLatch = CountDownLatch(1)

        scheduler.schedule(TaskId("task-2"), intervalMillis = 20) {
            executionCount.incrementAndGet()
            firstExecutionLatch.countDown()
        }
        firstExecutionLatch.await(2, TimeUnit.SECONDS)
        scheduler.cancel(TaskId("task-2"))
        val countAfterCancel = executionCount.get()

        Thread.sleep(100)

        assertTrue(executionCount.get() <= countAfterCancel + 1, "Po cancel liczba wykonań nie powinna dalej rosnąć")
    }

    @Test
    fun `scheduling with the same TaskId replaces the previous scheduling`() {
        val scheduler = InMemorySchedulerProvider()
        val firstTaskExecutions = AtomicInteger(0)
        val secondTaskExecutions = AtomicInteger(0)
        val secondLatch = CountDownLatch(1)

        scheduler.schedule(TaskId("task-3"), intervalMillis = 20) { firstTaskExecutions.incrementAndGet() }
        scheduler.schedule(TaskId("task-3"), intervalMillis = 20) {
            secondTaskExecutions.incrementAndGet()
            secondLatch.countDown()
        }

        secondLatch.await(2, TimeUnit.SECONDS)
        scheduler.cancel(TaskId("task-3"))

        assertTrue(secondTaskExecutions.get() >= 1, "Drugie zaplanowanie powinno zastąpić pierwsze i wykonywać się")
    }
}
