package midomail.domain.error

import midomail.domain.adapter.AdapterId
import midomail.domain.message.CorrelationId

/**
 * Klasyfikacja błędów (SPEC-0022-Error-Classification-Contract.md; 30-Infrastructure/34-Error-Handling.md,
 * §3) — sześć podtypów odpowiada dosłownie sześciu klasom błędów z dokumentu, zamknięta
 * taksonomia, rozszerzenie wymaga nowego ADR.
 *
 * Klasyfikacja zachodzi w punkcie kompozycji (np. `registerSafely()`), nie wewnątrz zamrożonych
 * kontraktów Core (`Registry`/`GatewayEngine`) — patrz SPEC-0022, §Gdzie klasyfikacja zachodzi.
 *
 * Throttling (`RateLimiter`/`Metrics.throttledCount`) celowo NIE ma odpowiednika tutaj
 * (34-Error-Handling.md §6: „Throttling nie jest błędem").
 */
sealed class GatewayError {
    abstract val message: String
    abstract val correlationId: CorrelationId?
    abstract val cause: Throwable?

    data class DomainError(
        override val message: String,
        override val correlationId: CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class InfrastructureError(
        override val message: String,
        override val correlationId: CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class AdapterError(
        val adapterId: AdapterId,
        override val message: String,
        override val correlationId: CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class ConfigurationError(
        override val message: String,
        override val correlationId: CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class SecurityError(
        override val message: String,
        override val correlationId: CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    /** Woła ten sam [midomail.domain.health.AlertSink] co [midomail.domain.health.HealthMonitor]. */
    data class CriticalError(
        override val message: String,
        override val correlationId: CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()
}
