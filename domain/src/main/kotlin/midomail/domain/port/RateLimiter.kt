package midomail.domain.port

import midomail.domain.adapter.AdapterId

enum class RateLimitedOperation { SEND, RECEIVE }

/** Wynik `tryAcquire` (SPEC-0011-Rate-Limiting-Contract.md, §Interfejs). */
sealed class RateLimitDecision {
    data object Allowed : RateLimitDecision()
    data class Throttled(val retryAfterMillis: Long) : RateLimitDecision()
}

/**
 * Port ograniczania przepustowości (10-Core/18-Porty.md, kategoria porty infrastrukturalne;
 * SPEC-0011-Rate-Limiting-Contract.md; ADR-0006-Rate-Limiting.md).
 *
 * Implementacja (np. token bucket w pamięci) jest szczegółem infrastruktury, nieznanym
 * Gateway Engine.
 */
interface RateLimiter {
    /**
     * Zwraca [RateLimitDecision.Allowed] natychmiast, jeśli dostępny jest żeton; w przeciwnym
     * razie [RateLimitDecision.Throttled] z wyliczonym czasem do odzyskania zdolności.
     */
    fun tryAcquire(adapterId: AdapterId, operation: RateLimitedOperation): RateLimitDecision
}
