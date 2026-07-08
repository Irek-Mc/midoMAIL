package midomail.domain.notification

import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Eskalacja nieautoryzowanych Alertów (38-Powiadomienia.md §5) — wyłącznie przez
 * [SchedulerProvider] jako zadanie cykliczne sprawdzające niepotwierdzone Alerty, **nie** przez
 * odrębny, równoległy mechanizm czasowy (§5, dosłownie). Trzeci prawdziwy konsument
 * `SchedulerProvider` po `EmailAdapter` (Iteracja 2.3) i `HealthMonitor` (Iteracja 4.3).
 *
 * [escalateAfterMillis] odzwierciedla `notifications.routing[].escalateAfterMinutes`
 * (SPEC-0005-Configuration-Model.md) — jedna wartość progu na poziom Alertu, nie wielopoziomowy
 * model eskalacji (schemat konfiguracji dokumentuje tylko jedno pole na regułę routingu). Po
 * przekroczeniu progu Alert jest ponownie dostarczany do TYCH SAMYCH kanałów przez
 * [NotificationRouter] („powtórzenie", jedna z dwóch opcji z §5) — powtarza się cyklicznie co
 * [escalateAfterMillis], dopóki [acknowledge] nie usunie Alertu ze śledzenia.
 */
class EscalationScheduler(
    private val schedulerProvider: SchedulerProvider,
    private val checkIntervalMillis: Long,
    private val escalateAfterMillis: (AlertLevel) -> Long?,
    private val router: NotificationRouter
) {
    private data class Tracked(val alert: Alert, var lastDeliveredAt: Instant)

    private val taskId = TaskId("escalation-check")
    private val activeAlerts = ConcurrentHashMap<AlertId, Tracked>()

    /** Rejestruje Alert do śledzenia eskalacji — bez efektu, jeśli [Alert.status] nie jest ACTIVE. */
    fun register(alert: Alert) {
        if (alert.status == midomail.domain.health.AlertStatus.ACTIVE) {
            activeAlerts[alert.alertId] = Tracked(alert, Instant.now())
        }
    }

    /** Zatrzymuje eskalację tego Alertu (38-Powiadomienia.md §5: „Eskalacja zatrzymuje się w momencie potwierdzenia"). */
    fun acknowledge(alertId: AlertId) {
        activeAlerts.remove(alertId)
    }

    /**
     * Migawka Alertów zarejestrowanych przez [register] i wciąż nieporzuconych przez [acknowledge]
     * (ADR-0026, 66-Monitoring.md §3-4) — niezależnie od tego, czy ich poziom ma skonfigurowany
     * próg eskalacji ([escalateAfterMillis] wpływa wyłącznie na to, czy [checkOnce] faktycznie
     * ponownie dostarcza Alert, nie na to, czy jest tu widoczny). Pełna historia wszystkich
     * Alertów (w tym nigdy nieprzekazanych do [register]) pozostaje dostępna przez
     * `EventStore`/`DiagnosticsFacade` (Faza 4), nie przez tę metodę.
     */
    fun activeAlerts(): List<Alert> = activeAlerts.values.map { it.alert }.toList()

    fun start() {
        schedulerProvider.schedule(taskId, checkIntervalMillis) { checkOnce() }
    }

    fun stop() {
        schedulerProvider.cancel(taskId)
    }

    private fun checkOnce() {
        val now = Instant.now()
        activeAlerts.values.forEach { tracked ->
            val threshold = escalateAfterMillis(tracked.alert.level) ?: return@forEach
            if (Duration.between(tracked.lastDeliveredAt, now).toMillis() >= threshold) {
                router.route(tracked.alert)
                tracked.lastDeliveredAt = now
            }
        }
    }
}
