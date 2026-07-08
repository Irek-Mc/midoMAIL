package midomail.domain.error

import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import java.time.Instant
import java.util.UUID

/**
 * Mapowanie [GatewayError.CriticalError] → [Alert] (SPEC-0022-Error-Classification-Contract.md,
 * §Punkt integracji z Alertami) — jedyny podtyp `GatewayError` generujący Alert; pozostałe pięć
 * podtypów jest raportowane przez logging/diagnostykę (Iteracje 4.11/4.12), nie eskaluje do Alertu.
 */
fun GatewayError.CriticalError.toAlert(source: SourceComponent): Alert = Alert(
    alertId = AlertId(UUID.randomUUID().toString()),
    level = AlertLevel.CRITICAL,
    source = source,
    timestamp = Instant.now(),
    status = AlertStatus.ACTIVE,
    recommendedAction = message,
    correlationId = correlationId
)
