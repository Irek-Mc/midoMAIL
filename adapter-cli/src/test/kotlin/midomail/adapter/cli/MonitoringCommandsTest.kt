package midomail.adapter.cli

import midomail.domain.monitoring.ResourceMonitor
import midomail.domain.monitoring.ResourceSnapshot
import kotlin.test.Test
import kotlin.test.assertTrue

class MonitoringCommandsTest {

    private class FakeResourceMonitor(private val snapshot: ResourceSnapshot) : ResourceMonitor {
        override fun snapshot(): ResourceSnapshot = snapshot
    }

    @Test
    fun `resources command reports available metrics`() {
        val dispatcher = CliDispatcher(MonitoringCommands(FakeResourceMonitor(ResourceSnapshot(ramUsedBytes = 100, ramTotalBytes = 200))).commands())

        val output = dispatcher.dispatch(arrayOf("resources"))

        assertTrue(output.contains("ram=100/200"))
    }

    @Test
    fun `resources command reports unavailable metrics readably, not as zero`() {
        val dispatcher = CliDispatcher(MonitoringCommands(FakeResourceMonitor(ResourceSnapshot())).commands())

        val output = dispatcher.dispatch(arrayOf("resources"))

        assertTrue(output.contains("cpuUsagePercent=niedostępne"))
    }
}
