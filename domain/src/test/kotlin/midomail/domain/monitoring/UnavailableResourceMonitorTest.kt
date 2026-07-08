package midomail.domain.monitoring

import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Potwierdza `UnavailableResourceMonitor` (ADR-0027, SA-14) — wszystkie pola `null`.
 */
class UnavailableResourceMonitorTest {

    @Test
    fun `snapshot reports every metric as unavailable`() {
        val snapshot = UnavailableResourceMonitor().snapshot()

        assertNull(snapshot.cpuUsagePercent)
        assertNull(snapshot.ramUsedBytes)
        assertNull(snapshot.ramTotalBytes)
        assertNull(snapshot.storageUsedBytes)
        assertNull(snapshot.storageTotalBytes)
        assertNull(snapshot.networkBytesReceived)
        assertNull(snapshot.networkBytesSent)
    }
}
