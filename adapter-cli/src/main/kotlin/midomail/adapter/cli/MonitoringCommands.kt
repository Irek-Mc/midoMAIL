package midomail.adapter.cli

import midomail.domain.monitoring.ResourceMonitor

/** Komendy CLI monitoringu zasobów (SPEC-0025, ADR-0027) — lustro `MonitoringEndpoints`. */
class MonitoringCommands(private val resourceMonitor: ResourceMonitor) {

    fun commands(): List<CliCommand> = listOf(ResourcesCommand())

    private inner class ResourcesCommand : CliCommand {
        override val name = "resources"
        override fun execute(args: List<String>): String {
            val snapshot = resourceMonitor.snapshot()
            return "cpuUsagePercent=${snapshot.cpuUsagePercent ?: "niedostępne"}\n" +
                "ram=${snapshot.ramUsedBytes ?: "niedostępne"}/${snapshot.ramTotalBytes ?: "niedostępne"}\n" +
                "storage=${snapshot.storageUsedBytes ?: "niedostępne"}/${snapshot.storageTotalBytes ?: "niedostępne"}\n" +
                "network=recv:${snapshot.networkBytesReceived ?: "niedostępne"} sent:${snapshot.networkBytesSent ?: "niedostępne"}"
        }
    }
}
