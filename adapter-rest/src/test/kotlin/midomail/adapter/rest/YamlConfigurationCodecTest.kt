package midomail.adapter.rest

import midomail.domain.configuration.AdapterConfigEntry
import midomail.domain.configuration.ConfigurationDocument
import midomail.domain.configuration.GatewayConfig
import midomail.domain.configuration.MessageStoreConfig
import midomail.domain.configuration.MonitoringConfig
import midomail.domain.configuration.NotificationChannelConfig
import midomail.domain.configuration.NotificationRoutingConfig
import midomail.domain.configuration.NotificationsConfig
import midomail.domain.configuration.RoutingConfig
import midomail.domain.configuration.RoutingRuleConditionsConfig
import midomail.domain.configuration.RoutingRuleConfig
import midomail.domain.configuration.SchedulerConfig
import midomail.domain.configuration.SchedulerTaskConfig
import midomail.domain.configuration.SecurityConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `YamlConfigurationCodec` (ADR-0032) — round-trip przez prawdziwe kodowanie/dekodowanie
 * YAML, nie atrapę.
 */
class YamlConfigurationCodecTest {

    private fun document(): ConfigurationDocument = ConfigurationDocument(
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
                    deliveryPolicy = "AT_LEAST_ONCE",
                    setPriority = "HIGH"
                )
            )
        ),
        adapters = listOf(
            AdapterConfigEntry(
                adapterId = "email-primary",
                type = "email",
                config = mapOf("smtp.host" to "smtp.gmail.com", "smtp.port" to "587")
            )
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
    fun `encode then decode round-trips to an equal ConfigurationDocument`() {
        val codec = YamlConfigurationCodec()
        val original = document()

        val yamlText = codec.encode(original)
        val decoded = codec.decode(yamlText)

        assertEquals(original, decoded)
    }

    @Test
    fun `encoded output is real, readable YAML text`() {
        val codec = YamlConfigurationCodec()

        val yamlText = codec.encode(document())

        assertTrue(yamlText.contains("instanceId"))
        assertTrue(yamlText.contains("midomail-01"))
        assertTrue(yamlText.contains("ruleId"))
        assertTrue(yamlText.contains("sms-to-email-default"))
    }

    @Test
    fun `decode parses hand-written YAML text matching SPEC-0005's example shape`() {
        val codec = YamlConfigurationCodec()
        val yamlText = """
            version: "2.0"
            gateway:
              instanceId: midomail-01
              logLevel: INFO
            routing:
              rules: []
            adapters: []
            scheduler:
              tasks: []
            security:
              secretStore: android-keystore
            monitoring:
              healthCheckIntervalSeconds: 30
            messageStore:
              retentionDays: 30
              deduplicationRetentionDays: 365
            notifications:
              channels: []
              routing: []
        """.trimIndent()

        val decoded = codec.decode(yamlText)

        assertEquals("midomail-01", decoded.gateway.instanceId)
        assertEquals("android-keystore", decoded.security.secretStore)
    }
}
