package midomail.domain.routing

import midomail.domain.adapter.AdapterId
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Potwierdza kontrakt Routing Engine z 10-Core/13-Routing.md i SPEC-0007-Routing-Contract.md.
 */
class RoutingEngineTest {

    private fun createMessage(
        sourceType: String = "gsm",
        destinationType: String = "email",
        messagePriority: MessagePriority = MessagePriority.NORMAL
    ): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference("<ext-1@localhost>"),
            messagePriority = messagePriority
        ),
        source = Channel(type = ChannelType(sourceType)),
        destination = Channel(type = ChannelType(destinationType)),
        payload = Payload(content = "treść")
    )

    private fun createRule(
        ruleId: String,
        priority: Int,
        enabled: Boolean = true,
        sourceChannel: String? = "gsm",
        destinationChannel: String? = null,
        setPriority: MessagePriority? = null
    ): RoutingRule = RoutingRule(
        ruleId = RuleId(ruleId),
        priority = priority,
        enabled = enabled,
        conditions = RoutingConditions(
            sourceChannel = sourceChannel?.let { ChannelType(it) },
            destinationChannel = destinationChannel?.let { ChannelType(it) }
        ),
        targetChannel = ChannelType("email"),
        targetAdapter = AdapterId("email-primary"),
        deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
        setPriority = setPriority,
        version = RuleVersion("1")
    )

    @Test
    fun `message matching a single rule is routed to its target`() {
        val engine = RoutingEngine(listOf(createRule("rule-1", priority = 100)))

        val decision = engine.route(createMessage())

        assertEquals(
            RoutingDecision.Routed(
                targetChannel = ChannelType("email"),
                targetAdapter = AdapterId("email-primary"),
                deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                messagePriority = MessagePriority.NORMAL
            ),
            decision
        )
    }

    @Test
    fun `message matching no rule returns NoMatch`() {
        val engine = RoutingEngine(listOf(createRule("rule-1", priority = 100, sourceChannel = "webhook")))

        val decision = engine.route(createMessage(sourceType = "gsm"))

        assertEquals(RoutingDecision.NoMatch, decision)
    }

    @Test
    fun `disabled rule is skipped even if it would otherwise match`() {
        val disabledHigherPriority = createRule("rule-disabled", priority = 200, enabled = false)
        val enabledLowerPriority = createRule("rule-enabled", priority = 100)
        val engine = RoutingEngine(listOf(disabledHigherPriority, enabledLowerPriority))

        val decision = engine.route(createMessage())

        assertIs<RoutingDecision.Routed>(decision)
        assertEquals(AdapterId("email-primary"), decision.targetAdapter)
    }

    @Test
    fun `higher priority rule wins over a lower priority matching rule`() {
        val lowPriorityRule = createRule("rule-low", priority = 100).copy(targetAdapter = AdapterId("adapter-low"))
        val highPriorityRule = createRule("rule-high", priority = 200).copy(targetAdapter = AdapterId("adapter-high"))
        val engine = RoutingEngine(listOf(lowPriorityRule, highPriorityRule))

        val decision = engine.route(createMessage())

        assertIs<RoutingDecision.Routed>(decision)
        assertEquals(AdapterId("adapter-high"), decision.targetAdapter)
    }

    @Test
    fun `tie in priority is resolved by declaration order - first declared wins - deterministic`() {
        val firstDeclared = createRule("rule-first", priority = 100).copy(targetAdapter = AdapterId("adapter-first"))
        val secondDeclared = createRule("rule-second", priority = 100).copy(targetAdapter = AdapterId("adapter-second"))
        val engine = RoutingEngine(listOf(firstDeclared, secondDeclared))

        val decision = engine.route(createMessage())

        assertIs<RoutingDecision.Routed>(decision)
        assertEquals(AdapterId("adapter-first"), decision.targetAdapter)
    }

    @Test
    fun `routing the same message repeatedly yields the same decision - deterministic`() {
        val engine = RoutingEngine(
            listOf(
                createRule("rule-1", priority = 100).copy(targetAdapter = AdapterId("adapter-a")),
                createRule("rule-2", priority = 100).copy(targetAdapter = AdapterId("adapter-b"))
            )
        )
        val message = createMessage()

        val decisions = (1..5).map { engine.route(message) }

        assertEquals(1, decisions.toSet().size, "Wszystkie wywołania powinny dać identyczny wynik")
    }

    @Test
    fun `setPriority overrides the message's own MessagePriority`() {
        val engine = RoutingEngine(listOf(createRule("rule-1", priority = 100, setPriority = MessagePriority.CRITICAL)))

        val decision = engine.route(createMessage(messagePriority = MessagePriority.LOW))

        assertIs<RoutingDecision.Routed>(decision)
        assertEquals(MessagePriority.CRITICAL, decision.messagePriority)
    }

    @Test
    fun `absence of setPriority preserves the message's own MessagePriority`() {
        val engine = RoutingEngine(listOf(createRule("rule-1", priority = 100, setPriority = null)))

        val decision = engine.route(createMessage(messagePriority = MessagePriority.HIGH))

        assertIs<RoutingDecision.Routed>(decision)
        assertEquals(MessagePriority.HIGH, decision.messagePriority)
    }

    @Test
    fun `rule with no sourceChannel condition matches any source channel`() {
        val engine = RoutingEngine(listOf(createRule("catch-all", priority = 0, sourceChannel = null)))

        val decision = engine.route(createMessage(sourceType = "rest"))

        assertIs<RoutingDecision.Routed>(decision)
    }

    /**
     * Regresja (Iteracja 3.13, znalezione na urządzeniu): reguła oparta wyłącznie o `sourceChannel`
     * dopasowywała KAŻDĄ wiadomość danego kanału źródłowego, w tym e-maile niebędące odpowiedzią na
     * przekazaną wiadomość z GSM (`destination.type` wtedy pozostaje `email`, nie `sms`) — próba
     * wysłania ich jako SMS kończyła się błędem (nieprawidłowy adres docelowy).
     */
    @Test
    fun `rule with a destinationChannel condition only matches messages whose destination matches`() {
        val engine = RoutingEngine(
            listOf(createRule("email-to-sms", priority = 100, sourceChannel = "email", destinationChannel = "sms"))
        )

        val correlatedReply = createMessage(sourceType = "email", destinationType = "sms")
        val unrelatedInboxEmail = createMessage(sourceType = "email", destinationType = "email")

        assertIs<RoutingDecision.Routed>(engine.route(correlatedReply))
        assertEquals(RoutingDecision.NoMatch, engine.route(unrelatedInboxEmail))
    }

    @Test
    fun `rule with no destinationChannel condition matches any destination channel`() {
        val engine = RoutingEngine(listOf(createRule("catch-all", priority = 0, destinationChannel = null)))

        val decision = engine.route(createMessage(destinationType = "webhook"))

        assertIs<RoutingDecision.Routed>(decision)
    }
}
