package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
import midomail.domain.administration.RoutingRuleChange

@Serializable
data class RoutingRuleChangeDto(
    val ruleId: String,
    val changeType: String,
    val version: String? = null,
    val timestamp: String
)

fun RoutingRuleChange.toDto(): RoutingRuleChangeDto = RoutingRuleChangeDto(
    ruleId = ruleId.value,
    changeType = changeType.name,
    version = version?.value,
    timestamp = timestamp.toString()
)
