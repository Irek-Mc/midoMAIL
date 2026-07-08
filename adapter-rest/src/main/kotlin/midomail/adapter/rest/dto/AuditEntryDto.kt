package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditEntryDto(
    val eventId: String,
    val eventType: String,
    val operation: String,
    val correlationId: String,
    val timestamp: String
)
