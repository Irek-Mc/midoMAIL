package midomail.domain.configuration

/** Błąd walidacji krzyżowej (ADR-0032, SPEC-0005 §Walidacja krzyżowa). */
data class ValidationError(val field: String, val message: String)

data class ValidationResult(val errors: List<ValidationError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

private val VALID_MESSAGE_PRIORITIES = setOf("LOW", "NORMAL", "HIGH", "CRITICAL")

/**
 * Walidator krzyżowy (ADR-0032-Konfiguracja-YAML-Pelna.md, SPEC-0005-Configuration-Model.md
 * §Walidacja krzyżowa — 10 reguł, ponumerowane komentarzami zgodnie z kolejnością w SPEC-0005).
 *
 * [platformProfile] i [pushSupported] są przekazywane przez wywołującego (punkt kompozycji zna
 * platformę uruchomieniową) — walidator sam nie odgaduje środowiska.
 *
 * **Świadomie poza zakresem:** dopasowanie `gsm.simSlot` do rzeczywiście dostępnego gniazda SIM
 * raportowanego przez platformę (reguła 2, częściowo) — walidator sprawdza wyłącznie, że wartość
 * jest nieujemna, nie ma dostępu do stanu sprzętu.
 */
class ConfigurationValidator {

    fun validate(document: ConfigurationDocument, platformProfile: String, pushSupported: Boolean = false): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        val adapterIds = document.adapters.map { it.adapterId }.toSet()
        val channelIds = document.notifications.channels.map { it.channelId }.toSet()

        // Reguła 1/8: adapter email wymaga pól SMTP/IMAP/credentials; enabled wymaga przejścia własnej walidacji.
        document.adapters.forEach { adapter ->
            if (adapter.type == "email" && (adapter.enabled)) {
                listOf("smtp.host", "smtp.port", "imap.host", "imap.port", "credentials.secretRef").forEach { field ->
                    if (adapter.config[field].isNullOrBlank()) {
                        errors.add(ValidationError("adapters[${adapter.adapterId}].config.$field", "Wymagane dla włączonego adaptera typu email"))
                    }
                }
            }
            // Reguła 2: gsm.simSlot nieujemny, jeśli podany (dopasowanie do rzeczywistego sprzętu poza zakresem).
            if (adapter.type == "gsm") {
                adapter.config["simSlot"]?.toIntOrNull()?.let { simSlot ->
                    if (simSlot < 0) {
                        errors.add(ValidationError("adapters[${adapter.adapterId}].config.simSlot", "simSlot musi być nieujemny"))
                    }
                }
            }
        }

        // Reguła 3: targetAdapter musi odnosić się do istniejącego adaptera.
        document.routing.rules.forEach { rule ->
            if (rule.targetAdapter !in adapterIds) {
                errors.add(ValidationError("routing.rules[${rule.ruleId}].targetAdapter", "Nieznany adapter: ${rule.targetAdapter}"))
            }
            // Reguła 5: setPriority musi być jedną z LOW/NORMAL/HIGH/CRITICAL.
            if (rule.setPriority != null && rule.setPriority !in VALID_MESSAGE_PRIORITIES) {
                errors.add(ValidationError("routing.rules[${rule.ruleId}].setPriority", "Nieprawidłowa wartość: ${rule.setPriority}"))
            }
            // Reguła 6 (conditions wyłącznie ChannelType) jest konstrukcyjnie wymuszona przez RoutingRuleConditionsConfig - brak dodatkowej walidacji.
        }
        // Reguła 4 (priorytet reguł nie musi być unikalny) - świadomy brak reguły, brak kodu.

        // Reguła 7: deduplicationRetentionDays >= retentionDays.
        if (document.messageStore.deduplicationRetentionDays < document.messageStore.retentionDays) {
            errors.add(
                ValidationError(
                    "messageStore.deduplicationRetentionDays",
                    "Musi być >= messageStore.retentionDays (${document.messageStore.retentionDays})"
                )
            )
        }

        // Reguła 9: taskId unikalny.
        val taskIds = document.scheduler.tasks.map { it.taskId }
        taskIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.forEach { duplicate ->
            errors.add(ValidationError("scheduler.tasks[$duplicate].taskId", "Zduplikowany taskId"))
        }

        // Reguła 10: security.secretStore zgodny z platformą.
        val allowedSecretStores = when (platformProfile) {
            "android" -> setOf("android-keystore")
            "jvm" -> setOf("env", "file")
            else -> emptySet()
        }
        if (allowedSecretStores.isNotEmpty() && document.security.secretStore !in allowedSecretStores) {
            errors.add(
                ValidationError(
                    "security.secretStore",
                    "'${document.security.secretStore}' nieprawidłowy dla platformy '$platformProfile' (dozwolone: $allowedSecretStores)"
                )
            )
        }

        // Reguła 11: notifications.routing[].channels[] musi odnosić się do istniejącego channelId.
        document.notifications.routing.forEach { routing ->
            routing.channels.filter { it !in channelIds }.forEach { unknownChannel ->
                errors.add(ValidationError("notifications.routing[${routing.level}].channels", "Nieznany kanał: $unknownChannel"))
            }
        }

        // Reguła 12: url/address wymagane warunkowo wg typu; PUSH wymaga wsparcia platformy.
        document.notifications.channels.forEach { channel ->
            when (channel.type) {
                "WEBHOOK" -> if (channel.url.isNullOrBlank()) {
                    errors.add(ValidationError("notifications.channels[${channel.channelId}].url", "Wymagane dla type=WEBHOOK"))
                }
                "EMAIL" -> if (channel.address.isNullOrBlank()) {
                    errors.add(ValidationError("notifications.channels[${channel.channelId}].address", "Wymagane dla type=EMAIL"))
                }
                "PUSH" -> if (!pushSupported) {
                    errors.add(ValidationError("notifications.channels[${channel.channelId}].type", "PUSH niewspierany przez tę platformę"))
                }
            }
        }

        return ValidationResult(errors)
    }
}
