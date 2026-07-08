package midomail.platform.jvm

import midomail.domain.configuration.RoutingRuleConditionsConfig
import midomail.domain.configuration.RoutingRuleConfig
import midomail.domain.message.ChannelType
import midomail.domain.message.MessagePriority
import midomail.domain.routing.RuleVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingRuleConfigMapperTest {

    @Test
    fun `maps required fields and defaults to RuleVersion 1`() {
        val config = RoutingRuleConfig(
            ruleId = "sms-to-email",
            priority = 100,
            targetChannel = "email",
            targetAdapter = "email-primary",
            deliveryPolicy = "AT_LEAST_ONCE"
        )

        val rule = config.toRoutingRule()

        assertEquals("sms-to-email", rule.ruleId.value)
        assertEquals(100, rule.priority)
        assertEquals(true, rule.enabled)
        assertEquals(ChannelType("email"), rule.targetChannel)
        assertEquals("email-primary", rule.targetAdapter.value)
        assertEquals("AT_LEAST_ONCE", rule.deliveryPolicy.value)
        assertEquals(RuleVersion("1"), rule.version)
        assertNull(rule.conditions.sourceChannel)
        assertNull(rule.setPriority)
    }

    @Test
    fun `maps conditions and setPriority when present`() {
        val config = RoutingRuleConfig(
            ruleId = "sms-to-email",
            priority = 100,
            conditions = RoutingRuleConditionsConfig(sourceChannel = "sms", destinationChannel = "email"),
            targetChannel = "email",
            targetAdapter = "email-primary",
            deliveryPolicy = "AT_LEAST_ONCE",
            setPriority = "HIGH"
        )

        val rule = config.toRoutingRule()

        assertEquals(ChannelType("sms"), rule.conditions.sourceChannel)
        assertEquals(ChannelType("email"), rule.conditions.destinationChannel)
        assertEquals(MessagePriority.HIGH, rule.setPriority)
    }

    @Test
    fun `maps enabled false through unchanged`() {
        val config = RoutingRuleConfig(
            ruleId = "disabled-rule",
            priority = 50,
            enabled = false,
            targetChannel = "email",
            targetAdapter = "email-primary",
            deliveryPolicy = "AT_LEAST_ONCE"
        )

        val rule = config.toRoutingRule()

        assertEquals(false, rule.enabled)
    }
}
