package midomail.domain.notification

import midomail.domain.health.Alert
import midomail.domain.health.AlertLevel

/**
 * Routing Alert → kanały (38-Powiadomienia.md §4) — `routing` mapuje poziom Alertu na jeden lub
 * więcej kanałów, dokładnie wzorem tabeli z §4 (CRITICAL→Push+Webhook, ERROR→Email+Webhook,
 * WARNING→Email, INFO→brak). Skonfigurowane bezpośrednio przez punkt kompozycji (Iteracja 4.13) —
 * ręcznie budowany obiekt, nie parser YAML (decyzja zakresu Fazy 4, Iteracja 4.0) — analogicznie do
 * tego, jak `RoutingEngine` przyjmuje `List<RoutingRule>` skonstruowaną bezpośrednio, nie przez
 * odrębny typ konfiguracji.
 *
 * Poziom bez wpisu w `routing` (domyślnie INFO — 38-Powiadomienia.md §4: „brak powiadomienia
 * zewnętrznego") zwraca pustą listę wyników, nie błąd.
 */
class NotificationRouter(private val routing: Map<AlertLevel, List<NotificationChannel>>) {

    fun route(alert: Alert): List<NotificationResult> {
        val channels = routing[alert.level] ?: emptyList()
        return channels.map { it.deliver(alert) }
    }
}
