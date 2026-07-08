package midomail.domain.configuration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `ConfigurationValidator` (ADR-0032) — po jednym teście na każdą regułę krzyżową z
 * SPEC-0005-Configuration-Model.md §Walidacja krzyżowa.
 */
class ConfigurationValidatorTest {

    private fun validDocument(): ConfigurationDocument = ConfigurationDocument(
        version = "2.0",
        gateway = GatewayConfig(instanceId = "midomail-01", logLevel = "INFO"),
        routing = RoutingConfig(
            rules = listOf(
                RoutingRuleConfig(
                    ruleId = "sms-to-email-default",
                    priority = 100,
                    conditions = RoutingRuleConditionsConfig(sourceChannel = "gsm"),
                    targetChannel = "email",
                    targetAdapter = "email-primary",
                    deliveryPolicy = "AT_LEAST_ONCE"
                )
            )
        ),
        adapters = listOf(
            AdapterConfigEntry(
                adapterId = "email-primary",
                type = "email",
                config = mapOf(
                    "smtp.host" to "smtp.gmail.com",
                    "smtp.port" to "587",
                    "imap.host" to "imap.gmail.com",
                    "imap.port" to "993",
                    "credentials.secretRef" to "email-primary/credentials"
                )
            ),
            AdapterConfigEntry(adapterId = "gsm-primary", type = "gsm", config = mapOf("simSlot" to "0"))
        ),
        scheduler = SchedulerConfig(tasks = listOf(SchedulerTaskConfig("email-poll", 60))),
        security = SecurityConfig(secretStore = "android-keystore"),
        monitoring = MonitoringConfig(healthCheckIntervalSeconds = 30),
        messageStore = MessageStoreConfig(retentionDays = 30, deduplicationRetentionDays = 365),
        notifications = NotificationsConfig(
            channels = listOf(NotificationChannelConfig("ops-email", "EMAIL", address = "ops@example.com")),
            routing = listOf(NotificationRoutingConfig("CRITICAL", listOf("ops-email"), escalateAfterMinutes = 5))
        )
    )

    @Test
    fun `a fully valid document passes validation`() {
        val result = ConfigurationValidator().validate(validDocument(), platformProfile = "android")

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `rule 1 - an enabled email adapter missing SMTP fields fails`() {
        val document = validDocument().let { doc ->
            doc.copy(adapters = doc.adapters.map { if (it.adapterId == "email-primary") it.copy(config = emptyMap()) else it })
        }

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("smtp.host") })
    }

    @Test
    fun `rule 2 - a negative gsm simSlot fails`() {
        val document = validDocument().let { doc ->
            doc.copy(adapters = doc.adapters.map { if (it.type == "gsm") it.copy(config = mapOf("simSlot" to "-1")) else it })
        }

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("simSlot") })
    }

    @Test
    fun `rule 3 - a routing rule referencing an unknown targetAdapter fails`() {
        val document = validDocument().let { doc ->
            doc.copy(routing = doc.routing.copy(rules = doc.routing.rules.map { it.copy(targetAdapter = "unknown-adapter") }))
        }

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("targetAdapter") })
    }

    @Test
    fun `rule 4 - duplicate rule priorities are explicitly not a validation error`() {
        val document = validDocument().let { doc ->
            doc.copy(
                routing = doc.routing.copy(
                    rules = doc.routing.rules + doc.routing.rules.single().copy(ruleId = "second-rule")
                )
            )
        }

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.isValid, "Priorytet reguł nie musi być unikalny - SPEC-0005 wprost to potwierdza")
    }

    @Test
    fun `rule 5 - an invalid setPriority value fails`() {
        val document = validDocument().let { doc ->
            doc.copy(routing = doc.routing.copy(rules = doc.routing.rules.map { it.copy(setPriority = "URGENT") }))
        }

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("setPriority") })
    }

    @Test
    fun `rule 7 - deduplicationRetentionDays lower than retentionDays fails`() {
        val document = validDocument().copy(messageStore = MessageStoreConfig(retentionDays = 400, deduplicationRetentionDays = 30))

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field == "messageStore.deduplicationRetentionDays" })
    }

    @Test
    fun `rule 9 - a duplicate scheduler taskId fails`() {
        val document = validDocument().copy(
            scheduler = SchedulerConfig(tasks = listOf(SchedulerTaskConfig("email-poll", 60), SchedulerTaskConfig("email-poll", 30)))
        )

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("taskId") })
    }

    @Test
    fun `rule 10 - a secretStore not matching the platform profile fails`() {
        val document = validDocument()

        val result = ConfigurationValidator().validate(document, platformProfile = "jvm")

        assertTrue(result.errors.any { it.field == "security.secretStore" })
    }

    @Test
    fun `rule 11 - a notification routing referencing an unknown channelId fails`() {
        val document = validDocument().copy(
            notifications = validDocument().notifications.copy(
                routing = listOf(NotificationRoutingConfig("CRITICAL", listOf("unknown-channel")))
            )
        )

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("channels") })
    }

    @Test
    fun `rule 12 - a WEBHOOK channel missing url fails`() {
        val document = validDocument().copy(
            notifications = NotificationsConfig(channels = listOf(NotificationChannelConfig("pagerduty", "WEBHOOK")))
        )

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertTrue(result.errors.any { it.field.contains("url") })
    }

    @Test
    fun `rule 12 - a PUSH channel fails when the platform does not support push`() {
        val document = validDocument().copy(
            notifications = NotificationsConfig(channels = listOf(NotificationChannelConfig("mobile-push", "PUSH")))
        )

        val result = ConfigurationValidator().validate(document, platformProfile = "android", pushSupported = false)

        assertTrue(result.errors.any { it.field.contains("type") })
    }

    @Test
    fun `rule 12 - a PUSH channel passes when the platform supports push`() {
        val document = validDocument().copy(
            notifications = NotificationsConfig(channels = listOf(NotificationChannelConfig("mobile-push", "PUSH")))
        )

        val result = ConfigurationValidator().validate(document, platformProfile = "android", pushSupported = true)

        assertTrue(result.isValid)
    }

    @Test
    fun `multiple violated rules are all reported together`() {
        val document = validDocument().copy(
            messageStore = MessageStoreConfig(retentionDays = 400, deduplicationRetentionDays = 30),
            scheduler = SchedulerConfig(tasks = listOf(SchedulerTaskConfig("task-1", 60), SchedulerTaskConfig("task-1", 30)))
        )

        val result = ConfigurationValidator().validate(document, platformProfile = "android")

        assertEquals(2, result.errors.size)
    }
}
