package midomail.domain.diagnostics

import midomail.domain.message.CorrelationId

/** Poziomy logowania (33-Logowanie.md §4). */
enum class LogLevel { INFO, WARN, ERROR }

/**
 * Log systemowy (33-Logowanie.md) — odrębny od `domain.adapter.Logger` (który pozostaje
 * zamrożony, per-adapterowy — wstrzykiwany przez `AdapterPorts`). `SystemLogger` jest
 * ogólnosystemowy, niesie `correlationId` (§4: „identyfikatory korelacyjne"), zasilany przez
 * `EventStore` (SPEC-0023) — implementacja domyślna [EventStoreSystemLogger].
 */
interface SystemLogger {
    fun log(level: LogLevel, message: String, correlationId: CorrelationId?, throwable: Throwable?)
}

fun SystemLogger.log(level: LogLevel, message: String, correlationId: CorrelationId? = null, throwable: Throwable? = null) =
    log(level, message, correlationId, throwable)

