package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
import midomail.domain.monitoring.ResourceSnapshot

@Serializable
data class ResourceSnapshotDto(
    val cpuUsagePercent: Double? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val storageUsedBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val networkBytesReceived: Long? = null,
    val networkBytesSent: Long? = null
)

fun ResourceSnapshot.toDto(): ResourceSnapshotDto = ResourceSnapshotDto(
    cpuUsagePercent, ramUsedBytes, ramTotalBytes, storageUsedBytes, storageTotalBytes, networkBytesReceived, networkBytesSent
)
