package midomail.domain.statistics

import midomail.domain.adapter.AdapterId
import java.time.Instant

/**
 * Migawka statystyk za okres [periodStart, periodEnd] (SPEC-0021-Statistics-Aggregation-Contract.md)
 * — przyrost liczników `Metrics` (SPEC-0015) W TYM OKRESIE, nie wartość skumulowana.
 */
data class MetricsSnapshot(
    val adapterId: AdapterId,
    val periodStart: Instant,
    val periodEnd: Instant,
    val messagesSent: Long,
    val messagesReceived: Long,
    val errorCount: Long,
    val throttledCount: Long
)
