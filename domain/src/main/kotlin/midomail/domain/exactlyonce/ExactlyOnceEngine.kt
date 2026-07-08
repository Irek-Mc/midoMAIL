package midomail.domain.exactlyonce

import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.port.InsertResult
import midomail.domain.port.MessageStore
import java.util.concurrent.atomic.AtomicLong

/** Wynik weryfikacji Exactly Once (10-Core/17-Exactly-Once.md; SPEC-0008-Exactly-Once-Contract.md). */
sealed class ExactlyOnceResult {
    data class Accepted(val message: GatewayMessage) : ExactlyOnceResult()
    data object Duplicate : ExactlyOnceResult()
}

/**
 * Liczniki skumulowane (ADR-0034-Dashboard-Status-i-Liczniki.md, 61-Dashboard.md §2,
 * 68-Statystyki.md §3). WYŁĄCZNIE `processed`/`duplicatesPrevented` — „Recovered"/„Failed"
 * pozostają poza zakresem [ExactlyOnceEngine] (SPEC-0008 ogranicza tę klasę do identyfikacji/
 * wykrywania duplikatów, nie routingu/dostarczania).
 */
data class ExactlyOnceCounters(val processed: Long, val duplicatesPrevented: Long)

/**
 * Mechanizm Exactly Once Processing (10-Core/17-Exactly-Once.md; SPEC-0008-Exactly-Once-Contract.md).
 *
 * Odpowiada wyłącznie za identyfikację komunikatów i wykrywanie duplikatów na podstawie
 * ExternalReference, przed utworzeniem nowego GatewayMessage (10-Core/17-Exactly-Once.md, §2).
 * Nie odpowiada za routing, komunikację z adapterami ani publikację zdarzeń końcowych —
 * to pozostaje odpowiedzialnością Gateway Engine (10-Core/11-Gateway-Engine.md, §5, krok 6).
 *
 * Weryfikacja duplikatu jest realizowana atomową operacją [MessageStore.insertIfAbsent]
 * (SPEC-0008, §Minimalny model operacji, krok 2) — bezpośrednie zabezpieczenie przed błędem
 * odtworzonym w wersji 1.x, gdzie sprawdzenie i zapis były rozdzielone i nieatomowe.
 */
class ExactlyOnceEngine(
    private val messageStore: MessageStore
) {
    private val processedCount = AtomicLong(0)
    private val duplicateCount = AtomicLong(0)

    fun processIfNew(externalReference: ExternalReference, message: GatewayMessage): ExactlyOnceResult =
        when (messageStore.insertIfAbsent(externalReference, message)) {
            InsertResult.INSERTED -> {
                processedCount.incrementAndGet()
                ExactlyOnceResult.Accepted(message)
            }
            InsertResult.ALREADY_EXISTS -> {
                duplicateCount.incrementAndGet()
                ExactlyOnceResult.Duplicate
            }
        }

    fun counters(): ExactlyOnceCounters = ExactlyOnceCounters(processedCount.get(), duplicateCount.get())
}
