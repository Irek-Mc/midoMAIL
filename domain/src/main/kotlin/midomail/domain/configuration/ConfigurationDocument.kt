package midomail.domain.configuration

/**
 * Model pełnej konfiguracji Gateway (ADR-0032-Konfiguracja-YAML-Pelna.md, odzwierciedla dokładnie
 * SPEC-0005-Configuration-Model.md §Przykładowy plik konfiguracyjny). Zero zależności zewnętrznych
 * — parsowanie YAML należy do `:adapter-rest` (ADR-0032); ten model jest wyłącznie strukturą
 * danych + celem [ConfigurationValidator].
 *
 * [AdapterConfigEntry.config] jest `Map<String, String>` (klucze typu `smtp.host`), nie
 * zagnieżdżonymi typami Kotlin per adapter — ten sam duch co `AdapterConfigurationSchema`
 * (ADR-0031), unikanie zależności na `:adapter-email`/`:adapter-gsm`.
 */
data class ConfigurationDocument(
    val version: String,
    val gateway: GatewayConfig,
    val routing: RoutingConfig,
    val adapters: List<AdapterConfigEntry>,
    val scheduler: SchedulerConfig,
    val security: SecurityConfig,
    val monitoring: MonitoringConfig,
    val messageStore: MessageStoreConfig,
    val notifications: NotificationsConfig
)

data class GatewayConfig(val instanceId: String, val logLevel: String)

data class RoutingRuleConditionsConfig(
    val sourceChannel: String? = null,
    val destinationChannel: String? = null
)

data class RoutingRuleConfig(
    val ruleId: String,
    val priority: Int,
    val enabled: Boolean = true,
    val conditions: RoutingRuleConditionsConfig = RoutingRuleConditionsConfig(),
    val targetChannel: String,
    val targetAdapter: String,
    val deliveryPolicy: String,
    val setPriority: String? = null
)

data class RoutingConfig(val rules: List<RoutingRuleConfig> = emptyList())

data class AdapterConfigEntry(
    val adapterId: String,
    val type: String,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap()
)

data class SchedulerTaskConfig(val taskId: String, val intervalSeconds: Int)

data class SchedulerConfig(val tasks: List<SchedulerTaskConfig> = emptyList())

data class SecurityConfig(val secretStore: String)

data class MonitoringConfig(val healthCheckIntervalSeconds: Int)

data class MessageStoreConfig(val retentionDays: Int, val deduplicationRetentionDays: Int = 365)

data class NotificationChannelConfig(
    val channelId: String,
    val type: String,
    val address: String? = null,
    val url: String? = null
)

data class NotificationRoutingConfig(
    val level: String,
    val channels: List<String>,
    val escalateAfterMinutes: Int? = null
)

data class NotificationsConfig(
    val channels: List<NotificationChannelConfig> = emptyList(),
    val routing: List<NotificationRoutingConfig> = emptyList()
)
