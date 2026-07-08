package midomail.domain.diagnostics

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Registry
import midomail.domain.event.Event
import midomail.domain.message.CorrelationId
import midomail.domain.message.GatewayMessage
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.EventStore
import midomail.domain.port.MessageStore
import midomail.domain.port.PageRequest

/**
 * Fasada diagnostyki (SPEC-0023-Diagnostics-Event-Store-Contract.md, §Diagnostics Facade) —
 * łączy trzy już istniejące źródła prawdy: `MessageStore` (ślad komunikatu), `EventStore`
 * (historia zdarzeń tego samego wątku), `Registry` (stan komponentów). Wyłącznie odczyt, żadnej
 * nowej logiki biznesowej (36-Diagnostyka.md §2: „nie wpływa na przebieg przetwarzania komunikatów").
 *
 * UI wyszukiwania/eksportu jest jawnie poza zakresem (67-Diagnostyka.md §4 to operacje EKRANU,
 * Faza 6) — ta klasa dostarcza wyłącznie zapytywalny kontrakt Core.
 */
class DiagnosticsFacade(
    private val messageStore: MessageStore,
    private val eventStore: EventStore,
    private val registry: Registry
) {
    /** „Ślad komunikatu" (67-Diagnostyka.md §3) — wiadomości i zdarzenia dla jednego wątku biznesowego. */
    data class MessageTrace(val messages: List<GatewayMessage>, val events: List<Event>)

    fun messageTrace(correlationId: CorrelationId): MessageTrace {
        val messages = messageStore.findByCorrelationId(correlationId)
        val events = eventStore.query(EventQueryFilter(correlationId = correlationId), PageRequest()).items
        return MessageTrace(messages, events)
    }

    /** „Stan komponentów" (67-Diagnostyka.md §3). */
    fun componentState(adapterId: AdapterId): AdapterState? = registry.stateOf(adapterId)
}
