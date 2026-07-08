package midomail.domain.administration

import midomail.domain.adapter.AdapterId
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.message.Channel
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingDecision
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Potwierdza administrację regułami routingu w czasie działania (ADR-0021).
 */
class RoutingRuleAdministrationTest {

    private fun rule(id: String, priority: Int = 100, version: String = "1"): RoutingRule = RoutingRule(
        ruleId = RuleId(id),
        priority = priority,
        targetChannel = ChannelType("email"),
        targetAdapter = AdapterId("email-primary"),
        deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
        version = RuleVersion(version)
    )

    private fun message(): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference("ext-1")
        ),
        source = Channel(type = ChannelType("sms")),
        destination = Channel(type = ChannelType("email")),
        payload = Payload(content = "treść")
    )

    @Test
    fun `add makes a rule visible in list and in a freshly built engine`() {
        val administration = RoutingRuleAdministration()

        administration.add(rule("rule-1"))

        assertEquals(1, administration.list().size)
        val decision = administration.buildEngine().route(message())
        assertIs<RoutingDecision.Routed>(decision)
    }

    @Test
    fun `adding a rule with a duplicate ruleId fails`() {
        val administration = RoutingRuleAdministration()
        administration.add(rule("rule-1"))

        assertFailsWith<IllegalArgumentException> {
            administration.add(rule("rule-1"))
        }
    }

    @Test
    fun `update replaces the rule and increments its RuleVersion automatically`() {
        val administration = RoutingRuleAdministration(listOf(rule("rule-1", priority = 100, version = "1")))

        administration.update(RuleId("rule-1"), rule("rule-1", priority = 200, version = "1"))

        val updated = administration.list().single()
        assertEquals(200, updated.priority)
        assertEquals(RuleVersion("2"), updated.version)
    }

    @Test
    fun `updating a non-existent rule fails`() {
        val administration = RoutingRuleAdministration()

        assertFailsWith<IllegalArgumentException> {
            administration.update(RuleId("unknown"), rule("unknown"))
        }
    }

    @Test
    fun `remove drops the rule from list and from a freshly built engine`() {
        val administration = RoutingRuleAdministration(listOf(rule("rule-1")))

        administration.remove(RuleId("rule-1"))

        assertTrue(administration.list().isEmpty())
        val decision = administration.buildEngine().route(message())
        assertEquals(RoutingDecision.NoMatch, decision)
    }

    @Test
    fun `buildEngine reflects the current mutable state, not a stale snapshot`() {
        val administration = RoutingRuleAdministration()
        administration.add(rule("rule-1"))
        val firstEngine = administration.buildEngine()

        administration.remove(RuleId("rule-1"))
        val secondEngine = administration.buildEngine()

        assertIs<RoutingDecision.Routed>(firstEngine.route(message()))
        assertEquals(RoutingDecision.NoMatch, secondEngine.route(message()))
    }

    // --- history() (ADR-0029) ---

    @Test
    fun `history records an ADDED entry with the rule's version`() {
        val administration = RoutingRuleAdministration()

        administration.add(rule("rule-1", version = "1"))

        val entry = administration.history().single()
        assertEquals(RuleId("rule-1"), entry.ruleId)
        assertEquals(RoutingRuleChangeType.ADDED, entry.changeType)
        assertEquals(RuleVersion("1"), entry.version)
    }

    @Test
    fun `history records an UPDATED entry with the incremented version`() {
        val administration = RoutingRuleAdministration(listOf(rule("rule-1", version = "1")))

        administration.update(RuleId("rule-1"), rule("rule-1", priority = 200, version = "1"))

        val entry = administration.history().single()
        assertEquals(RoutingRuleChangeType.UPDATED, entry.changeType)
        assertEquals(RuleVersion("2"), entry.version)
    }

    @Test
    fun `rules provided at construction are not logged as ADDED history entries`() {
        val administration = RoutingRuleAdministration(listOf(rule("rule-1")))

        assertTrue(administration.history().isEmpty())
    }

    @Test
    fun `history records a REMOVED entry with a null version`() {
        val administration = RoutingRuleAdministration(listOf(rule("rule-1")))

        administration.remove(RuleId("rule-1"))

        val entry = administration.history().single()
        assertEquals(RoutingRuleChangeType.REMOVED, entry.changeType)
        assertEquals(null, entry.version)
    }

    @Test
    fun `removing a non-existent rule does not add a history entry`() {
        val administration = RoutingRuleAdministration()

        administration.remove(RuleId("unknown"))

        assertTrue(administration.history().isEmpty())
    }

    @Test
    fun `history entries are recorded in chronological order`() {
        val administration = RoutingRuleAdministration()

        administration.add(rule("rule-1", version = "1"))
        administration.update(RuleId("rule-1"), rule("rule-1", version = "1"))
        administration.remove(RuleId("rule-1"))

        val changeTypes = administration.history().map { it.changeType }
        assertEquals(
            listOf(RoutingRuleChangeType.ADDED, RoutingRuleChangeType.UPDATED, RoutingRuleChangeType.REMOVED),
            changeTypes
        )
    }
}
