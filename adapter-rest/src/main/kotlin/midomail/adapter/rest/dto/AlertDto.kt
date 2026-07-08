package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
import midomail.domain.health.Alert

@Serializable
data class AlertDto(
    val alertId: String,
    val level: String,
    val source: String,
    val timestamp: String,
    val status: String,
    val recommendedAction: String? = null,
    val correlationId: String? = null
)

fun Alert.toDto(): AlertDto = AlertDto(
    alertId = alertId.value,
    level = level.name,
    source = source.value,
    timestamp = timestamp.toString(),
    status = status.name,
    recommendedAction = recommendedAction,
    correlationId = correlationId?.value
)
