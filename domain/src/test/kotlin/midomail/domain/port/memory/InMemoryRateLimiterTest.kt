package midomail.domain.port.memory

import midomail.domain.adapter.AdapterId
import midomail.domain.port.RateLimitDecision
import midomail.domain.port.RateLimitedOperation
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt Rate Limiter z SPEC-0011-Rate-Limiting-Contract.md przez referencyjną
 * implementację token bucket [InMemoryRateLimiter].
 */
class InMemoryRateLimiterTest {

    @Test
    fun `tryAcquire returns Allowed while tokens are available`() {
        val limiter = InMemoryRateLimiter(
            mapOf((AdapterId("gsm-primary") to RateLimitedOperation.SEND) to RateLimitConfig(capacity = 3, refillPerMinute = 60))
        )

        repeat(3) {
            assertIs<RateLimitDecision.Allowed>(limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND))
        }
    }

    @Test
    fun `tryAcquire returns Throttled with a positive retryAfterMillis once capacity is exhausted`() {
        val limiter = InMemoryRateLimiter(
            mapOf((AdapterId("gsm-primary") to RateLimitedOperation.SEND) to RateLimitConfig(capacity = 1, refillPerMinute = 60))
        )
        limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND)

        val decision = limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND)

        assertIs<RateLimitDecision.Throttled>(decision)
        assertTrue(decision.retryAfterMillis > 0)
    }

    @Test
    fun `a pair with no configuration has no limit - always Allowed`() {
        val limiter = InMemoryRateLimiter(configs = emptyMap())

        repeat(100) {
            assertIs<RateLimitDecision.Allowed>(limiter.tryAcquire(AdapterId("unconfigured"), RateLimitedOperation.SEND))
        }
    }

    @Test
    fun `SEND and RECEIVE for the same adapter have independent buckets`() {
        val limiter = InMemoryRateLimiter(
            mapOf(
                (AdapterId("gsm-primary") to RateLimitedOperation.SEND) to RateLimitConfig(capacity = 1, refillPerMinute = 60)
            )
        )
        limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND)

        // RECEIVE nie ma własnej konfiguracji - nie powinien być throttled przez zużycie kubełka SEND.
        assertIs<RateLimitDecision.Allowed>(limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.RECEIVE))
    }

    @Test
    fun `tokens refill over time, eventually allowing another acquisition`() {
        // Wysokie tempo uzupełniania (600/min = 10/s) - żeton dostępny ponownie po ok. 100ms.
        val limiter = InMemoryRateLimiter(
            mapOf((AdapterId("gsm-primary") to RateLimitedOperation.SEND) to RateLimitConfig(capacity = 1, refillPerMinute = 600))
        )
        limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND)
        val throttled = limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND)
        assertIs<RateLimitDecision.Throttled>(throttled)

        Thread.sleep(throttled.retryAfterMillis + 20)

        assertIs<RateLimitDecision.Allowed>(limiter.tryAcquire(AdapterId("gsm-primary"), RateLimitedOperation.SEND))
    }
}
