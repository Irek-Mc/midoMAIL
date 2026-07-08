package midomail.domain.gateway

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza `GatewayInfo` (ADR-0034-Dashboard-Status-i-Liczniki.md).
 */
class GatewayInfoTest {

    @Test
    fun `holds the version and start time it was constructed with`() {
        val startedAt = Instant.parse("2026-07-06T00:00:00Z")

        val info = GatewayInfo(version = "2.0.0", startedAt = startedAt)

        assertEquals("2.0.0", info.version)
        assertEquals(startedAt, info.startedAt)
    }
}
