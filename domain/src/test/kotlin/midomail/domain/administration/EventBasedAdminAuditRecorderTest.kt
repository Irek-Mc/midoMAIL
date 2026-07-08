package midomail.domain.administration

import midomail.domain.event.EventCategory
import midomail.domain.event.SourceComponent
import midomail.domain.port.EventQueryFilter
import midomail.domain.port.PageRequest
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza [EventBasedAdminAuditRecorder] (SPEC-0024, §Uwierzytelnianie i audyt).
 */
class EventBasedAdminAuditRecorderTest {

    @Test
    fun `record publishes and stores exactly one ADMINISTRATIVE event`() {
        val eventPublisher = InMemoryEventPublisher()
        val eventStore = InMemoryEventStore()
        val recorder = EventBasedAdminAuditRecorder(eventPublisher, eventStore, SourceComponent("AdminHttpServer"))

        recorder.record("POST /adapters/disable?id=email-primary", authenticated = true)

        assertEquals(1, eventPublisher.events().size)
        assertEquals(EventCategory.ADMINISTRATIVE, eventPublisher.events().single().category)
        val stored = eventStore.query(EventQueryFilter(category = EventCategory.ADMINISTRATIVE), PageRequest())
        assertEquals(1, stored.items.size)
    }

    @Test
    fun `an authentication failure is recorded with a distinct eventType`() {
        val eventPublisher = InMemoryEventPublisher()
        val eventStore = InMemoryEventStore()
        val recorder = EventBasedAdminAuditRecorder(eventPublisher, eventStore, SourceComponent("AdminHttpServer"))

        recorder.record("POST /adapters/disable?id=email-primary", authenticated = false)

        assertTrue(eventPublisher.events().single().eventType.value.contains("auth_failure"))
    }
}
