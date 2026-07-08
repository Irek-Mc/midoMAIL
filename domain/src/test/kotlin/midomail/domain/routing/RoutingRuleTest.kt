package midomail.domain.routing

import midomail.domain.adapter.AdapterId
import midomail.domain.message.ChannelType
import midomail.domain.message.MessagePriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt RoutingRule z 10-Core/13-Routing.md, §Model reguł routingu i
 * SPEC-0007-Routing-Contract.md.
 */
class RoutingRuleTest {

    private fun createRule(
        setPriority: MessagePriority? = null,
        conditions: RoutingConditions = RoutingConditions(sourceChannel = ChannelType("gsm"))
    ): RoutingRule = RoutingRule(
        ruleId = RuleId("sms-to-email-default"),
        priority = 100,
        conditions = conditions,
        targetChannel = ChannelType("email"),
        targetAdapter = AdapterId("email-primary"),
        deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
        setPriority = setPriority,
        version = RuleVersion("1")
    )

    @Test
    fun `RuleId, DeliveryPolicy and RuleVersion reject blank value`() {
        assertFailsWith<IllegalArgumentException> { RuleId("") }
        assertFailsWith<IllegalArgumentException> { DeliveryPolicy("") }
        assertFailsWith<IllegalArgumentException> { RuleVersion("") }
    }

    @Test
    fun `enabled defaults to true`() {
        assertTrue(createRule().enabled)
    }

    @Test
    fun `conditions default to no restriction - sourceChannel is null`() {
        val rule = RoutingRule(
            ruleId = RuleId("catch-all"),
            priority = 0,
            targetChannel = ChannelType("email"),
            targetAdapter = AdapterId("email-primary"),
            deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
            version = RuleVersion("1")
        )

        assertNull(rule.conditions.sourceChannel)
    }

    @Test
    fun `rule can be built without setPriority - no override of MessagePriority`() {
        val rule = createRule(setPriority = null)

        assertNull(rule.setPriority)
    }

    @Test
    fun `rule can be built with setPriority distinct from the rule's own priority`() {
        val rule = createRule(setPriority = MessagePriority.HIGH).copy(priority = 200)

        assertEquals(MessagePriority.HIGH, rule.setPriority)
        assertEquals(200, rule.priority)
    }

    @Test
    fun `conditions reference only ChannelType - not address or AdapterId`() {
        val rule = createRule(conditions = RoutingConditions(sourceChannel = ChannelType("webhook")))

        assertEquals(ChannelType("webhook"), rule.conditions.sourceChannel)
    }
}
