package midomail.domain.port.memory

import midomail.domain.adapter.AdapterId
import midomail.domain.port.RateLimitDecision
import midomail.domain.port.RateLimitedOperation
import midomail.domain.port.RateLimiter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Konfiguracja kubełka żetonów dla pary (AdapterId, operacja)
 * (SPEC-0005-Configuration-Model.md, §rateLimiting).
 */
data class RateLimitConfig(val capacity: Long, val refillPerMinute: Long)

private class TokenBucket(private val config: RateLimitConfig) {
    private val lock = ReentrantLock()
    private val refillPerNano: Double = config.refillPerMinute.toDouble() / 60_000_000_000.0
    private var availableTokens: Double = config.capacity.toDouble()
    private var lastRefillNanos: Long = System.nanoTime()

    fun tryAcquire(): RateLimitDecision = lock.withLock {
        refill()
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0
            RateLimitDecision.Allowed
        } else {
            val tokensNeeded = 1.0 - availableTokens
            val nanosNeeded = tokensNeeded / refillPerNano
            val millisNeeded = (nanosNeeded / 1_000_000.0).toLong().coerceAtLeast(1)
            RateLimitDecision.Throttled(retryAfterMillis = millisNeeded)
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedNanos = now - lastRefillNanos
        availableTokens = (availableTokens + elapsedNanos * refillPerNano).coerceAtMost(config.capacity.toDouble())
        lastRefillNanos = now
    }
}

/**
 * Referencyjna implementacja [RateLimiter] — algorytm token bucket, osobny kubełek dla każdej
 * pary (AdapterId, operacja) (SPEC-0011-Rate-Limiting-Contract.md, §Algorytm — token bucket).
 *
 * Para bez wpisu w [configs] nie ma limitu (SPEC-0005-Configuration-Model.md, §rateLimiting:
 * `capacity`/`refillPerMinute` domyślnie „brak limitu") — [tryAcquire] zawsze zwraca
 * [RateLimitDecision.Allowed].
 */
class InMemoryRateLimiter(
    private val configs: Map<Pair<AdapterId, RateLimitedOperation>, RateLimitConfig>
) : RateLimiter {

    private val buckets = ConcurrentHashMap<Pair<AdapterId, RateLimitedOperation>, TokenBucket>()

    override fun tryAcquire(adapterId: AdapterId, operation: RateLimitedOperation): RateLimitDecision {
        val key = adapterId to operation
        val config = configs[key] ?: return RateLimitDecision.Allowed
        val bucket = buckets.computeIfAbsent(key) { TokenBucket(config) }
        return bucket.tryAcquire()
    }
}
