package midomail.domain.statistics

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Metrics
import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Agreguje `Adapter.metrics()` w migawki okresowe (SPEC-0021-Statistics-Aggregation-Contract.md) —
 * czwarty prawdziwy konsument `SchedulerProvider` (po `EmailAdapter`, `HealthMonitor`,
 * `EscalationScheduler`).
 *
 * [adapters] dostarczane bezpośrednio przez punkt kompozycji, tym samym wzorcem co `HealthMonitor`
 * (SPEC-0020, §Skąd HealthMonitor bierze adaptery) — `Registry` nie udostępnia instancji.
 *
 * Migawki przechowywane we własnej, wewnętrznej liście — niezależnie od `MessageStore`, spełnia to
 * 68-Statystyki.md §5 („zagregowane metryki przeżywają purge danych źródłowych") strukturalnie:
 * nigdy nie są z niego odczytywane.
 */
class StatisticsAggregator(
    private val adapters: List<Adapter>,
    private val schedulerProvider: SchedulerProvider,
    private val snapshotIntervalMillis: Long
) {
    private val taskId = TaskId("statistics-snapshot")
    private val lastMetrics = ConcurrentHashMap<AdapterId, Metrics>()
    private val snapshots = CopyOnWriteArrayList<MetricsSnapshot>()

    @Volatile
    private var periodStart: Instant = Instant.now()

    fun start() {
        periodStart = Instant.now()
        schedulerProvider.schedule(taskId, snapshotIntervalMillis) { takeSnapshot() }
    }

    fun stop() {
        schedulerProvider.cancel(taskId)
    }

    fun snapshots(): List<MetricsSnapshot> = snapshots.toList()

    private fun takeSnapshot() {
        val now = Instant.now()
        adapters.forEach { adapter ->
            val current = adapter.metrics()
            val previous = lastMetrics.put(adapter.adapterId, current)
            if (previous != null) {
                snapshots.add(
                    MetricsSnapshot(
                        adapterId = adapter.adapterId,
                        periodStart = periodStart,
                        periodEnd = now,
                        messagesSent = current.messagesSent - previous.messagesSent,
                        messagesReceived = current.messagesReceived - previous.messagesReceived,
                        errorCount = current.errorCount - previous.errorCount,
                        throttledCount = current.throttledCount - previous.throttledCount
                    )
                )
            }
        }
        periodStart = now
    }
}
