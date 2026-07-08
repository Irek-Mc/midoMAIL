package midomail.domain.diagnostics

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterLifecycle
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Registry
import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Potwierdza `DiagnosticsFacade` (SPEC-0023, §Diagnostics Facade) — łączy `MessageStore`,
 * `EventStore`, `Registry` w jeden odczyt śladu komunikatu.
 */
class DiagnosticsFacadeTest {

    @Test
    fun `messageTrace combines messages and events sharing the same correlationId`() {
        val messageStore = InMemoryMessageStore()
        val eventStore = InMemoryEventStore()
        val registry = Registry(InMemoryEventPublisher())
        val facade = DiagnosticsFacade(messageStore, eventStore, registry)

        val correlationId = CorrelationId("correlation-1")
        val message = GatewayMessage(
            identity = Identity(
                messageId = MessageId("message-1"),
                correlationId = correlationId,
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference("ext-1")
            ),
            source = Channel(type = ChannelType("email")),
            destination = Channel(type = ChannelType("email")),
            payload = Payload(content = "treść")
        )
        messageStore.insertIfAbsent(ExternalReference("ext-1"), message)
        eventStore.record(
            Event(
                eventId = EventId("event-1"),
                eventType = EventType("domain.message_routed"),
                eventVersion = EventVersion("1.0"),
                category = EventCategory.DOMAIN,
                timestamp = Instant.now(),
                correlationId = correlationId,
                sourceComponent = SourceComponent("GatewayEngine"),
                payload = message
            )
        )

        val trace = facade.messageTrace(correlationId)

        assertEquals(1, trace.messages.size)
        assertEquals(MessageId("message-1"), trace.messages.single().identity.messageId)
        assertEquals(1, trace.events.size)
        assertEquals(EventId("event-1"), trace.events.single().eventId)
    }

    @Test
    fun `componentState reflects Registry's lifecycle state`() {
        val registry = Registry(InMemoryEventPublisher())
        val facade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)
        val adapterId = AdapterId("email-primary")
        registry.register(adapterId, object : AdapterLifecycle {
            override fun start() {}
            override fun stop() {}
        })

        assertEquals(AdapterState.READY, facade.componentState(adapterId))
    }

    @Test
    fun `componentState returns null for an unregistered adapter`() {
        val registry = Registry(InMemoryEventPublisher())
        val facade = DiagnosticsFacade(InMemoryMessageStore(), InMemoryEventStore(), registry)

        assertNull(facade.componentState(AdapterId("unknown")))
    }
}
