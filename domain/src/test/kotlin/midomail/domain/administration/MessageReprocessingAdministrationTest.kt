package midomail.domain.administration

import midomail.domain.adapter.AdapterId
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Potwierdza ręczne ponowne przetworzenie (ADR-0028-Message-Reprocessing.md) na prawdziwym
 * `GatewayEngine` (`ExactlyOnceEngine`+`RoutingEngine`+`InMemoryMessageStore` rzeczywiste) — nie
 * omija Exactly Once, przechodzi przez nią ponownie po jawnym unieważnieniu rekordu.
 */
class MessageReprocessingAdministrationTest {

    private class FakeGatewayOutbound : GatewayOutbound {
        val sentMessages = mutableListOf<GatewayMessage>()
        override fun send(message: GatewayMessage) {
            sentMessages.add(message)
        }
    }

    private fun buildEngine(messageStore: InMemoryMessageStore, outbound: FakeGatewayOutbound): GatewayEngine = GatewayEngine(
        exactlyOnceEngine = ExactlyOnceEngine(messageStore),
        routingEngine = RoutingEngine(
            listOf(
                RoutingRule(
                    ruleId = RuleId("rule-1"),
                    priority = 100,
                    targetChannel = ChannelType("email"),
                    targetAdapter = AdapterId("email-primary"),
                    deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                    version = RuleVersion("1")
                )
            )
        ),
        eventPublisher = InMemoryEventPublisher(),
        gatewayOutbound = outbound
    )

    private fun originalMessage(): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference("<ext-1@localhost>")
        ),
        source = Channel(type = ChannelType("gsm")),
        destination = Channel(type = ChannelType("email")),
        payload = Payload(content = "Treść oryginalna")
    )

    @Test
    fun `reprocess re-submits the message through GatewayEngine with a new MessageId`() {
        val messageStore = InMemoryMessageStore()
        val outbound = FakeGatewayOutbound()
        val engine = buildEngine(messageStore, outbound)
        val original = originalMessage()
        engine.receive(original)
        val administration = MessageReprocessingAdministration(messageStore, engine)

        val result = administration.reprocess(ExternalReference("<ext-1@localhost>"))

        assertIs<MessageReprocessingAdministration.ReprocessResult.Reprocessed>(result)
        assertNotEquals(MessageId("message-1"), result.message.identity.messageId)
        assertEquals(2, outbound.sentMessages.size, "oryginalna wiadomość i ponownie przetworzona - obie dostarczone")
    }

    @Test
    fun `reprocess links the new message to the original via CausationId`() {
        val messageStore = InMemoryMessageStore()
        val engine = buildEngine(messageStore, FakeGatewayOutbound())
        engine.receive(originalMessage())
        val administration = MessageReprocessingAdministration(messageStore, engine)

        val result = administration.reprocess(ExternalReference("<ext-1@localhost>"))

        assertIs<MessageReprocessingAdministration.ReprocessResult.Reprocessed>(result)
        assertEquals("message-1", result.message.identity.causationId?.value)
    }

    @Test
    fun `reprocess preserves the original message record queryable by its own MessageId`() {
        val messageStore = InMemoryMessageStore()
        val engine = buildEngine(messageStore, FakeGatewayOutbound())
        engine.receive(originalMessage())
        val administration = MessageReprocessingAdministration(messageStore, engine)

        administration.reprocess(ExternalReference("<ext-1@localhost>"))

        val stored = messageStore.findById(MessageId("message-1"))
        assertEquals(originalMessage().payload, stored?.payload, "GatewayEngine.receive() zapisuje komunikat w stanie VALIDATED, nie ACCEPTED - to nadal ten sam oryginalny rekord (treść/tożsamość niezmieniona)")
        assertEquals(originalMessage().identity, stored?.identity)
    }

    @Test
    fun `reprocess on an unknown ExternalReference returns NotFound`() {
        val messageStore = InMemoryMessageStore()
        val engine = buildEngine(messageStore, FakeGatewayOutbound())
        val administration = MessageReprocessingAdministration(messageStore, engine)

        val result = administration.reprocess(ExternalReference("<unknown@localhost>"))

        assertEquals(MessageReprocessingAdministration.ReprocessResult.NotFound, result)
    }

    @Test
    fun `an automatic duplicate submission before reprocessing is still blocked normally`() {
        val messageStore = InMemoryMessageStore()
        val outbound = FakeGatewayOutbound()
        val engine = buildEngine(messageStore, outbound)
        engine.receive(originalMessage())

        val secondAttempt = engine.receive(originalMessage().copy(identity = originalMessage().identity.copy(messageId = MessageId("message-2"))))

        assertTrue(secondAttempt is midomail.domain.gateway.ProcessingResult.Duplicate, "Exactly Once nadal blokuje automatyczne duplikaty - reprocessing to jedyna świadoma ścieżka obejścia")
        assertEquals(1, outbound.sentMessages.size)
    }
}
