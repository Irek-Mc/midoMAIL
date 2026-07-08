package midomail.domain.monitoring

/**
 * Migawka zasobów hosta/procesu (ADR-0027-Resource-Monitor.md, 66-Monitoring.md §2). Wszystkie
 * pola nullable — nie każda platforma/uprawnienie udostępnia każdą metrykę; brak wartości jest
 * jawnie reprezentowany, nie zerem czy wyjątkiem.
 */
data class ResourceSnapshot(
    val cpuUsagePercent: Double? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val storageUsedBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val networkBytesReceived: Long? = null,
    val networkBytesSent: Long? = null
)

/** Port monitoringu zasobów (ADR-0027) — implementacje platformowe w punkcie kompozycji. */
fun interface ResourceMonitor {
    fun snapshot(): ResourceSnapshot
}
