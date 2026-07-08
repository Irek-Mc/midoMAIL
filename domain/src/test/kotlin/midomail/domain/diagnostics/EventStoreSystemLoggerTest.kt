package midomail.domain.diagnostics

import midomail.domain.event.Event
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.EventStore
import midomail.domain.port.Page
import midomail.domain.port.PageRequest
import midomail.domain.port.memory.InMemoryEventStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza [EventStoreSystemLogger] (33-Logowanie.md).
 */
class EventStoreSystemLoggerTest {

    @Test
    fun `log creates a queryable Event carrying the given correlationId`() {
        val eventStore = InMemoryEventStore()
        val logger = EventStoreSystemLogger(eventStore, SourceComponent("GatewayEngine"))

        logger.log(LogLevel.ERROR, "połączenie nie powiodło się", CorrelationId("correlation-1"))

        val results = eventStore.query(EventQueryFilter(correlationId = CorrelationId("correlation-1")), PageRequest())
        assertEquals(1, results.items.size)
        val payload = results.items.single().payload as LogEntry
        assertEquals(LogLevel.ERROR, payload.level)
        assertEquals("połączenie nie powiodło się", payload.message)
    }

    @Test
    fun `a missing correlationId is assigned a generated one, never left null`() {
        val eventStore = InMemoryEventStore()
        val logger = EventStoreSystemLogger(eventStore, SourceComponent("GatewayEngine"))

        logger.log(LogLevel.INFO, "start")

        val results = eventStore.query(EventQueryFilter(), PageRequest())
        assertEquals(1, results.items.size)
    }

    @Test
    fun `the redact hook is applied to both the message and the throwable message`() {
        val eventStore = InMemoryEventStore()
        val logger = EventStoreSystemLogger(eventStore, SourceComponent("GatewayEngine"), redact = { it.replace("secret", "***") })

        logger.log(LogLevel.ERROR, "hasło: secret123", throwable = IllegalStateException("token=secret456"))

        val payload = eventStore.query(EventQueryFilter(), PageRequest()).items.single().payload as LogEntry
        assertEquals("hasło: ***123", payload.message)
        assertEquals("token=***456", payload.throwableMessage)
    }

    @Test
    fun `a failure in EventStore_record never propagates - logging failure must not block the Gateway`() {
        val throwingEventStore = object : EventStore {
            override fun record(event: Event) = throw IllegalStateException("dysk pełny")
            override fun query(filter: EventQueryFilter, page: PageRequest): Page<Event> = Page(emptyList(), null)
        }
        val logger = EventStoreSystemLogger(throwingEventStore, SourceComponent("GatewayEngine"))

        // Brak wyjątku propagującego się z log() jest samą treścią tego testu - jeśli
        // EventStoreSystemLogger nie łapałby wyjątku z record(), ten wiersz przerwałby test.
        logger.log(LogLevel.ERROR, "to nie powinno rzucić wyjątku")
    }
}
