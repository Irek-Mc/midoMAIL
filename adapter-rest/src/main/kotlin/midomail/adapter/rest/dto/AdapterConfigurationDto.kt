package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdapterConfigurationDto(val fields: Map<String, String?>)
