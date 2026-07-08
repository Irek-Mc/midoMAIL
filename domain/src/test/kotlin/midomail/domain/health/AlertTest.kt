package midomail.domain.health

import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Potwierdza kontrakt modelu Alertu (SPEC-0018-Alert-Model-Contract.md).
 */
class AlertTest {

    @Test
    fun `AlertId rejects a blank value`() {
        assertFailsWith<IllegalArgumentException> { AlertId("") }
    }

    @Test
    fun `AlertId rejects a whitespace-only value`() {
        assertFailsWith<IllegalArgumentException> { AlertId("   ") }
    }

    @Test
    fun `two Alerts with identical field values are equal`() {
        val timestamp = Instant.parse("2026-07-06T12:00:00Z")
        val first = Alert(
            alertId = AlertId("alert-1"),
            level = AlertLevel.CRITICAL,
            source = SourceComponent("health-monitor"),
            timestamp = timestamp,
            status = AlertStatus.ACTIVE
        )
        val second = Alert(
            alertId = AlertId("alert-1"),
            level = AlertLevel.CRITICAL,
            source = SourceComponent("health-monitor"),
            timestamp = timestamp,
            status = AlertStatus.ACTIVE
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `recommendedAction and correlationId default to null`() {
        val alert = Alert(
            alertId = AlertId("alert-1"),
            level = AlertLevel.WARNING,
            source = SourceComponent("email-primary"),
            timestamp = Instant.now(),
            status = AlertStatus.ACTIVE
        )

        assertNull(alert.recommendedAction)
        assertNull(alert.correlationId)
    }

    @Test
    fun `correlationId is carried when the Alert originates from a specific message`() {
        val alert = Alert(
            alertId = AlertId("alert-1"),
            level = AlertLevel.ERROR,
            source = SourceComponent("gateway-engine"),
            timestamp = Instant.now(),
            status = AlertStatus.ACTIVE,
            correlationId = CorrelationId("correlation-1")
        )

        assertEquals(CorrelationId("correlation-1"), alert.correlationId)
    }
}
