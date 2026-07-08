package midomail.platform.android

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.StatFs
import midomail.domain.monitoring.ResourceMonitor
import midomail.domain.monitoring.ResourceSnapshot

/**
 * Implementacja Android [ResourceMonitor] (ADR-0027-Resource-Monitor.md) — RAM przez
 * `ActivityManager.MemoryInfo`, Storage przez `StatFs` na katalogu danych aplikacji, Network przez
 * `TrafficStats` (żadne z tych API nie wymaga uprawnień specjalnych ponad standardowe dla aplikacji).
 *
 * **CPU jawnie `null`** — Android od API 26 ogranicza dostęp do `/proc/stat` dla aplikacji bez
 * uprawnień systemowych; nie ma publicznego API do odczytu ogólnego zużycia CPU hosta. Ograniczenie
 * platformy, udokumentowane w ADR-0027, nie luka implementacyjna do naprawienia.
 */
class AndroidResourceMonitor(private val context: Context) : ResourceMonitor {

    override fun snapshot(): ResourceSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val statFs = StatFs(context.filesDir.path)
        val storageTotal = statFs.blockCountLong * statFs.blockSizeLong
        val storageAvailable = statFs.availableBlocksLong * statFs.blockSizeLong

        return ResourceSnapshot(
            cpuUsagePercent = null,
            ramUsedBytes = memoryInfo.totalMem - memoryInfo.availMem,
            ramTotalBytes = memoryInfo.totalMem,
            storageUsedBytes = storageTotal - storageAvailable,
            storageTotalBytes = storageTotal,
            networkBytesReceived = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() },
            networkBytesSent = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() }
        )
    }
}
