package midomail.platform.jvm

import midomail.domain.adapter.AdapterId
import midomail.domain.configuration.RoutingRuleConfig
import midomail.domain.message.ChannelType
import midomail.domain.message.MessagePriority
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingConditions
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion

/**
 * Mapuje `RoutingRuleConfig` (`:domain.configuration`, ADR-0032) na `RoutingRule` (`:domain.routing`,
 * Faza 1) — reguły wczytane z pliku YAML startowego seedują `RoutingRuleAdministration`
 * (ADR-0036, SA-19). Wersja zawsze `RuleVersion("1")` — `RoutingRuleConfig` nie niesie wersji
 * (wersjonowanie jest odpowiedzialnością `RoutingRuleAdministration` od momentu utworzenia
 * instancji w pamięci, nie samego pliku konfiguracyjnego).
 */
fun RoutingRuleConfig.toRoutingRule(): RoutingRule = RoutingRule(
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
    version = RuleVersion("1")
)
