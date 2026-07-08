package midomail.domain.notification

import midomail.domain.health.Alert

/**
 * Port dostarczania powiadomień (SPEC-0019-Notification-Channel-Contract.md; ADR-0016) — odrębny
 * od `Adapter` (ADR-0016-Notification-Channel-Port.md): brak cyklu życia, brak rejestracji w
 * `Registry`, nie uczestniczy w `RoutingEngine`/`ExactlyOnceEngine`.
 */
interface NotificationChannel {
    fun deliver(alert: Alert): NotificationResult
}

/**
 * `Unavailable` odróżniony od `Failed` — brak możliwości platformowej (np. Push na JVM/Linux
 * headless) nie jest niepowodzeniem transportu i nie podlega polityce Retry (34-Error-Handling.md
 * §5/§6), w przeciwieństwie do `Failed`.
 */
sealed class NotificationResult {
    data object Delivered : NotificationResult()
    data class Failed(val reason: String) : NotificationResult()
    data class Unavailable(val reason: String) : NotificationResult()
}
