package midomail.adapter.cli

import midomail.domain.adapter.AdapterId
import midomail.domain.administration.MessageReprocessingAdministration
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageCommandsTest {

    private class NoopGatewayOutbound : GatewayOutbound {
        override fun send(message: GatewayMessage) {}
    }

    private fun sampleMessage(externalReference: String = "<ext-1@localhost>"): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference(externalReference)
        ),
        source = Channel(type = ChannelType("gsm")),
        destination = Channel(type = ChannelType("email")),
        payload = Payload(content = "Treść testowa")
    )

    private fun setup(): Triple<CliDispatcher, InMemoryMessageStore, GatewayEngine> {
        val messageStore = InMemoryMessageStore()
        val engine = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(messageStore),
            routingEngine = RoutingEngine(
                listOf(
                    RoutingRule(
                        ruleId = RuleId("rule-1"), priority = 100, targetChannel = ChannelType("email"),
                        targetAdapter = AdapterId("email-primary"), deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                        version = RuleVersion("1")
                    )
                )
            ),
            eventPublisher = InMemoryEventPublisher(),
            gatewayOutbound = NoopGatewayOutbound()
        )
        val dispatcher = CliDispatcher(MessageCommands(messageStore, MessageReprocessingAdministration(messageStore, engine)).commands())
        return Triple(dispatcher, messageStore, engine)
    }

    @Test
    fun `messages command lists messages matching the channel filter`() {
        val (dispatcher, _, engine) = setup()
        engine.receive(sampleMessage())

        val output = dispatcher.dispatch(arrayOf("messages", "gsm"))

        assertTrue(output.contains("correlation-1"))
    }

    @Test
    fun `messages-find by externalReference returns the message`() {
        val (dispatcher, _, engine) = setup()
        engine.receive(sampleMessage())

        val output = dispatcher.dispatch(arrayOf("messages-find", "externalReference", "<ext-1@localhost>"))

        assertTrue(output.contains("correlation-1"))
    }

    @Test
    fun `messages-find for an unknown message reports not found`() {
        val (dispatcher, _, _) = setup()

        val output = dispatcher.dispatch(arrayOf("messages-find", "messageId", "unknown"))

        assertEquals("Nie znaleziono komunikatu", output)
    }

    @Test
    fun `messages-reprocess re-submits the message`() {
        val (dispatcher, _, engine) = setup()
        engine.receive(sampleMessage())

        val output = dispatcher.dispatch(arrayOf("messages-reprocess", "<ext-1@localhost>"))

        assertTrue(output.startsWith("OK: nowy MessageId="))
    }

    @Test
    fun `messages-reprocess for an unknown ExternalReference reports not found`() {
        val (dispatcher, _, _) = setup()

        val output = dispatcher.dispatch(arrayOf("messages-reprocess", "unknown"))

        assertTrue(output.contains("Nie znaleziono komunikatu"))
    }
}
