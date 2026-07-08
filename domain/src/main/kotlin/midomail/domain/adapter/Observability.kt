package midomail.domain.adapter

/**
 * Ocena gotowości adaptera (SPEC-0015-Adapter-Observability-Contract.md, §HealthStatus).
 * `details` niesie zwięzłą przyczynę degradacji, gdy `healthy == false`.
 */
data class HealthStatus(
    val healthy: Boolean,
    val details: String? = null
)

/**
 * Metryki adaptera, w tym stan Rate Limitera (SPEC-0015-Adapter-Observability-Contract.md,
 * §Metrics; SPEC-0011-Rate-Limiting-Contract.md, §Obserwowalność).
 *
 * [availableTokens] jest `null`, jeśli dla adaptera nie skonfigurowano limitu przepustowości.
 */
data class Metrics(
    val availableTokens: Long?,
    val throttledCount: Long,
    val cumulativeThrottlingEvents: Long,
    val messagesSent: Long,
    val messagesReceived: Long,
    val errorCount: Long
)
