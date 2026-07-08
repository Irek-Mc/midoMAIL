package midomail.domain.gateway

import midomail.domain.adapter.AdapterId
import midomail.domain.event.Event
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.MessagePriority
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.processing.ProcessingContext
import midomail.domain.processing.ProcessingState
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingConditions
import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Potwierdza pełny przepływ z 10-Core/11-Gateway-Engine.md, §5 (kroki 1–8) — kryterium wyjścia
 * Fazy 1 (50-Quality/55-Roadmap.md): syntetyczny GatewayMessage przechodzi cały przepływ i
 * publikuje zdarzenie; drugi komunikat o tym samym ExternalReference nie jest przetworzony
 * ponownie; wynik routingu deterministyczny — bez jednego rzeczywistego adaptera i bez UI.
 */
class GatewayEngineTest {

    private class FakeGatewayOutbound : GatewayOutbound {
        val sentMessages = mutableListOf<GatewayMessage>()
        override fun send(message: GatewayMessage) {
            sentMessages.add(message)
        }
    }

    private fun createMessage(
        messageId: String = "message-1",
        externalReference: String = "<ext-1@localhost>",
        processingState: ProcessingState = ProcessingState.ACCEPTED,
        messagePriority: MessagePriority = MessagePriority.NORMAL
    ): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId(messageId),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference(externalReference),
            messagePriority = messagePriority
        ),
        source = Channel(type = ChannelType("gsm")),
        destination = Channel(type = ChannelType("email")),
        payload = Payload(content = "tak tak"),
        processingContext = ProcessingContext(processingState)
    )

    private fun createRoutingRule(setPriority: MessagePriority? = null): RoutingRule = RoutingRule(
        ruleId = RuleId("sms-to-email-default"),
        priority = 100,
        conditions = RoutingConditions(sourceChannel = ChannelType("gsm")),
        targetChannel = ChannelType("email"),
        targetAdapter = AdapterId("email-primary"),
        deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
        setPriority = setPriority,
        version = RuleVersion("1")
    )

    private inner class Fixture(rules: List<RoutingRule> = listOf(createRoutingRule())) {
        val messageStore = InMemoryMessageStore()
        val eventPublisher = InMemoryEventPublisher()
        val gatewayOutbound = FakeGatewayOutbound()
        val engine = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(messageStore),
            routingEngine = RoutingEngine(rules),
            eventPublisher = eventPublisher,
            gatewayOutbound = gatewayOutbound
        )
    }

    @Test
    fun `kryterium wyjscia Fazy 1 - a synthetic message passes through the whole flow and reaches SENT`() {
        val fixture = Fixture()

        val result = fixture.engine.receive(createMessage())

        assertIs<ProcessingResult.Accepted>(result)
        assertEquals(ProcessingState.SENT, result.message.processingContext.processingState)
    }

    @Test
    fun `kryterium wyjscia Fazy 1 - the message is published as a domain event`() {
        val fixture = Fixture()

        fixture.engine.receive(createMessage())

        assertTrue(fixture.eventPublisher.events().isNotEmpty())
        val event: Event = fixture.eventPublisher.events().single()
        assertEquals("domain.message_routed", event.eventType.value)
    }

    @Test
    fun `kryterium wyjscia Fazy 1 - a second message with the same ExternalReference is not processed again`() {
        val fixture = Fixture()
        val firstMessage = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>")
        val secondMessage = createMessage(messageId = "message-2", externalReference = "<ext-1@localhost>")

        val firstResult = fixture.engine.receive(firstMessage)
        val secondResult = fixture.engine.receive(secondMessage)

        assertIs<ProcessingResult.Accepted>(firstResult)
        assertEquals(ProcessingResult.Duplicate, secondResult)
        assertEquals(1, fixture.gatewayOutbound.sentMessages.size, "Port wyjściowy powinien otrzymać komunikat dokładnie raz")
    }

    @Test
    fun `kryterium wyjscia Fazy 1 - routing result is deterministic across repeated calls with independent messages`() {
        val fixture = Fixture()

        val decisions = (1..5).map { index ->
            fixture.engine.receive(createMessage(messageId = "message-$index", externalReference = "<ext-$index@localhost>"))
        }

        decisions.forEach { result ->
            assertIs<ProcessingResult.Accepted>(result)
            assertEquals(AdapterId("email-primary"), result.message.destination.adapterId)
        }
    }

    @Test
    fun `a message not in ACCEPTED state is rejected before Exactly Once or Routing run`() {
        val fixture = Fixture()

        val result = fixture.engine.receive(createMessage(processingState = ProcessingState.ROUTED))

        assertIs<ProcessingResult.Rejected>(result)
        assertTrue(fixture.eventPublisher.events().isEmpty())
        assertTrue(fixture.gatewayOutbound.sentMessages.isEmpty())
    }

    @Test
    fun `a message matching no routing rule fails with NoRoute and FAILED state`() {
        val fixture = Fixture(rules = emptyList())

        val result = fixture.engine.receive(createMessage())

        assertIs<ProcessingResult.NoRoute>(result)
        assertEquals(ProcessingState.FAILED, result.message.processingContext.processingState)
        assertTrue(fixture.gatewayOutbound.sentMessages.isEmpty())
    }

    @Test
    fun `routing decision updates destination channel type and adapterId`() {
        val fixture = Fixture()

        val result = fixture.engine.receive(createMessage())

        assertIs<ProcessingResult.Accepted>(result)
        assertEquals(ChannelType("email"), result.message.destination.type)
        assertEquals(AdapterId("email-primary"), result.message.destination.adapterId)
    }

    @Test
    fun `setPriority from the matched rule overrides the message's own MessagePriority`() {
        val fixture = Fixture(rules = listOf(createRoutingRule(setPriority = MessagePriority.CRITICAL)))

        val result = fixture.engine.receive(createMessage(messagePriority = MessagePriority.LOW))

        assertIs<ProcessingResult.Accepted>(result)
        assertEquals(MessagePriority.CRITICAL, result.message.identity.messagePriority)
    }

    @Test
    fun `the message handed to the output port is in HANDED_TO_ADAPTER state at the moment of send`() {
        val fixture = Fixture()

        fixture.engine.receive(createMessage())

        assertEquals(1, fixture.gatewayOutbound.sentMessages.size)
        assertEquals(ProcessingState.HANDED_TO_ADAPTER, fixture.gatewayOutbound.sentMessages.single().processingContext.processingState)
    }
}
