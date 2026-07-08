package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
import midomail.domain.configuration.ValidationResult

@Serializable
data class ValidationErrorDto(val field: String, val message: String)

@Serializable
data class ValidationResultDto(val valid: Boolean, val errors: List<ValidationErrorDto>)

fun ValidationResult.toDto(): ValidationResultDto = ValidationResultDto(
    valid = isValid,
    errors = errors.map { ValidationErrorDto(it.field, it.message) }
)
