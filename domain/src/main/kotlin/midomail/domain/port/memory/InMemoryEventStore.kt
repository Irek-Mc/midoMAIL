package midomail.domain.port.memory

import midomail.domain.event.Event
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.EventStore
import midomail.domain.port.Page
import midomail.domain.port.PageRequest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Referencyjna implementacja [EventStore] w pamięci (SPEC-0023-Diagnostics-Event-Store-Contract.md)
 * — sortowanie zawsze malejąco po czasie (zdarzenia są z natury uporządkowane czasowo, w
 * przeciwieństwie do `MessageStore`, który potrzebuje wielu pól sortowania).
 */
class InMemoryEventStore : EventStore {

    private val events = CopyOnWriteArrayList<Event>()

    override fun record(event: Event) {
        events.add(event)
    }

    override fun query(filter: EventQueryFilter, page: PageRequest): Page<Event> {
        val filtered = events.filter { matches(it, filter) }.sortedByDescending { it.timestamp }

        val afterCursor = page.cursor?.let { decodeCursor(it) }
        val startIndex = if (afterCursor == null) {
            0
        } else {
            filtered.indexOfFirst { event ->
                val key = event.timestamp.toEpochMilli()
                key < afterCursor.first || (key == afterCursor.first && event.eventId.value < afterCursor.second)
            }.let { if (it == -1) filtered.size else it }
        }

        val pageItems = filtered.drop(startIndex).take(page.size)
        val nextCursor = if (startIndex + page.size < filtered.size) {
            pageItems.lastOrNull()?.let { encodeCursor(it.timestamp.toEpochMilli(), it.eventId.value) }
        } else {
            null
        }

        return Page(items = pageItems, nextCursor = nextCursor)
    }

    private fun matches(event: Event, filter: EventQueryFilter): Boolean {
        if (filter.correlationId != null && event.correlationId != filter.correlationId) return false
        if (filter.eventType != null && event.eventType != filter.eventType) return false
        if (filter.category != null && event.category != filter.category) return false
        if (filter.sourceComponent != null && event.sourceComponent != filter.sourceComponent) return false
        if (filter.createdAfter != null && event.timestamp.isBefore(filter.createdAfter)) return false
        if (filter.createdBefore != null && event.timestamp.isAfter(filter.createdBefore)) return false
        return true
    }

    private fun encodeCursor(sortKey: Long, eventId: String): String =
        Base64.getEncoder().encodeToString("$sortKey|$eventId".toByteArray())

    private fun decodeCursor(cursor: String): Pair<Long, String> {
        val decoded = String(Base64.getDecoder().decode(cursor))
        val separatorIndex = decoded.indexOf('|')
        return decoded.substring(0, separatorIndex).toLong() to decoded.substring(separatorIndex + 1)
    }
}
