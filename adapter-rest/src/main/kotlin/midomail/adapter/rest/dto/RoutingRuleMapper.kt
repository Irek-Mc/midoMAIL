package midomail.adapter.rest.dto

import midomail.domain.adapter.AdapterId
import midomail.domain.message.ChannelType
import midomail.domain.message.MessagePriority
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingConditions
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion

/**
 * Mapowanie `RoutingRule`↔`RoutingRuleDto` (SPEC-0024, §4 Routing) — dzielone przez
 * `ReadStateEndpoints` (odczyt) i `RoutingEndpoints` (zapis, Iteracja 5.13), analogicznie do
 * `EmailMessageMapper` w `:adapter-email`.
 */
fun RoutingRule.toDto(): RoutingRuleDto = RoutingRuleDto(
    ruleId = ruleId.value,
    priority = priority,
    enabled = enabled,
    conditions = RoutingConditionsDto(
        sourceChannel = conditions.sourceChannel?.value,
        destinationChannel = conditions.destinationChannel?.value
    ),
    targetChannel = targetChannel.value,
    targetAdapter = targetAdapter.value,
    deliveryPolicy = deliveryPolicy.value,
    setPriority = setPriority?.name,
    version = version.value
)

fun RoutingRuleDto.toDomain(): RoutingRule = RoutingRule(
    ruleId = RuleId(ruleId),
    priority = priority,
    enabled = enabled,
    conditions = RoutingConditions(
        sourceChannel = conditions.sourceChannel?.let { ChannelType(it) },
        destinationChannel = conditions.destinationChannel?.let { ChannelType(it) }
    ),
    targetChannel = ChannelType(targetChannel),
    targetAdapter = AdapterId(targetAdapter),
    deliveryPolicy = DeliveryPolicy(deliveryPolicy),
    setPriority = setPriority?.let { MessagePriority.valueOf(it) },
    version = RuleVersion(version)
)
