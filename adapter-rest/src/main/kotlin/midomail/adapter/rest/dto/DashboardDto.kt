package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class GatewayStatusDto(val status: String, val version: String, val uptimeSeconds: Long)

@Serializable
data class ExactlyOnceCountersDto(val processed: Long, val duplicatesPrevented: Long)
