package midomail.domain.health

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.event.SourceComponent
import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Stan zagregowany „worst-of" (ADR-0034-Dashboard-Status-i-Liczniki.md). */
enum class AggregateHealthStatus { READY, DEGRADED }

/**
 * Agreguje `Adapter.health()` w jeden stan systemowy (SPEC-0020-Health-Aggregation-Contract.md) —
 * drugi po `EmailAdapter` (Iteracja 2.3) prawdziwy konsument [SchedulerProvider], zamykający punkt
 * Roadmapy „Scheduler — automatyczne, cykliczne sprawdzanie" dla stanu zdrowia, nie tylko poczty.
 *
 * [adapters] dostarczane bezpośrednio przez punkt kompozycji, nie przez zapytanie do `Registry` —
 * `Registry` świadomie przechowuje wyłącznie `AdapterLifecycle`, nie pełny `Adapter`; ten sam
 * problem rozwiązano już raz w Fazie 3 przez `AdapterRegistryOutbound` (SPEC-0020, §Skąd
 * HealthMonitor bierze adaptery).
 *
 * Reguła agregacji: „worst-of" — wszystkie zdrowe → stan systemowy zdrowy; co najmniej jeden
 * niezdrowy → zdegradowany. `Alert` publikowany wyłącznie przy PRZEJŚCIU stanu pojedynczego
 * adaptera (nie przy każdym cyklu) — baseline przed pierwszym sprawdzeniem to `healthy = true`
 * (optymistyczne założenie: adapter dopiero co przeszedł `start()` w Registry), więc adapter
 * niezdrowy już przy pierwszym cyklu też generuje Alert (cicha awaria przy starcie byłaby gorsza
 * niż nadmiarowy Alert).
 */
class HealthMonitor(
    private val adapters: List<Adapter>,
    private val schedulerProvider: SchedulerProvider,
    private val checkIntervalMillis: Long,
    private val alertSink: AlertSink
) {
    private val taskId = TaskId("health-monitor-check")
    private val lastKnownHealthy = ConcurrentHashMap<AdapterId, Boolean>()

    fun start() {
        adapters.forEach { lastKnownHealthy[it.adapterId] = true }
        schedulerProvider.schedule(taskId, checkIntervalMillis) { checkOnce() }
    }

    fun stop() {
        schedulerProvider.cancel(taskId)
    }

    /**
     * Migawka zagregowanego stanu systemowego „worst-of" (ADR-0034-Dashboard-Status-i-Liczniki.md,
     * 61-Dashboard.md §2 „Status Gateway"). Adapter bez wpisu w [lastKnownHealthy] (przed
     * pierwszym cyklem [checkOnce]) traktowany optymistycznie jako zdrowy — ta sama konwencja co
     * baseline w [start].
     */
    fun currentStatus(): AggregateHealthStatus =
        if (adapters.all { lastKnownHealthy[it.adapterId] ?: true }) AggregateHealthStatus.READY
        else AggregateHealthStatus.DEGRADED

    private fun checkOnce() {
        adapters.forEach { adapter ->
            val currentlyHealthy = adapter.health().healthy
            val previouslyHealthy = lastKnownHealthy.put(adapter.adapterId, currentlyHealthy)
            if (previouslyHealthy != null && previouslyHealthy != currentlyHealthy) {
                alertSink.onAlert(transitionAlert(adapter, currentlyHealthy))
            }
        }
    }

    private fun transitionAlert(adapter: Adapter, currentlyHealthy: Boolean): Alert = Alert(
        alertId = AlertId(UUID.randomUUID().toString()),
        level = if (currentlyHealthy) AlertLevel.INFO else AlertLevel.WARNING,
        source = SourceComponent(adapter.adapterId.value),
        timestamp = Instant.now(),
        status = if (currentlyHealthy) AlertStatus.RESOLVED else AlertStatus.ACTIVE,
        recommendedAction = if (currentlyHealthy) null else "Sprawdź stan adaptera ${adapter.adapterId.value} (${adapter.health().details ?: "brak szczegółów"})"
    )
}
