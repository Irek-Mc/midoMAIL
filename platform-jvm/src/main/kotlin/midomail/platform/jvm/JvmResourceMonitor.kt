package midomail.platform.jvm

import midomail.domain.monitoring.ResourceMonitor
import midomail.domain.monitoring.ResourceSnapshot
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Implementacja JVM/Linux [ResourceMonitor] (ADR-0027-Resource-Monitor.md, ADR-0036) — zastępuje
 * `UnavailableResourceMonitor` (Faza 6, świadomy stub). CPU/RAM przez
 * `com.sun.management.OperatingSystemMXBean` (standardowa część OpenJDK/Oracle JDK, nie wymaga
 * nowej zależności). Storage przez `java.nio.file.FileStore` na skonfigurowanym katalogu danych.
 *
 * **Network jawnie `null`** — standardowy JDK nie ma przenośnego API do zliczania bajtów
 * sieciowych całego hosta (odpowiednik Androidowego `TrafficStats` nie istnieje) — udokumentowane
 * ograniczenie, spójne z tym, że [ResourceSnapshot] ma już wszystkie pola nullable (ADR-0027).
 */
class JvmResourceMonitor(private val dataDirectory: Path) : ResourceMonitor {

    private val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
        as com.sun.management.OperatingSystemMXBean

    override fun snapshot(): ResourceSnapshot {
        val fileStore = Files.getFileStore(dataDirectory)
        val cpuLoad = operatingSystemMXBean.cpuLoad.takeIf { it >= 0.0 }

        return ResourceSnapshot(
            cpuUsagePercent = cpuLoad?.times(100.0),
            ramUsedBytes = operatingSystemMXBean.totalMemorySize - operatingSystemMXBean.freeMemorySize,
            ramTotalBytes = operatingSystemMXBean.totalMemorySize,
            storageUsedBytes = fileStore.totalSpace - fileStore.usableSpace,
            storageTotalBytes = fileStore.totalSpace,
            networkBytesReceived = null,
            networkBytesSent = null
        )
    }
}
