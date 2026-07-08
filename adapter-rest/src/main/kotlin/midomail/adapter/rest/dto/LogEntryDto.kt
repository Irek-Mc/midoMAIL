package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class LogEntryDto(
    val eventId: String,
    val level: String,
    val message: String,
    val correlationId: String,
    val sourceComponent: String,
    val timestamp: String
)
