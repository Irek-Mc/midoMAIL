package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
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

/**
 * DTO serializowalne (`kaml`/`kotlinx.serialization`, ADR-0032-Konfiguracja-YAML-Pelna.md) —
 * mirror `midomail.domain.configuration.ConfigurationDocument` (i typów zagnieżdżonych). `:domain`
 * ma zero zależności zewnętrznych, więc `@Serializable` nie może żyć na typach domenowych
 * bezpośrednio — ten sam wzorzec co `RoutingRuleDto`/`RoutingRuleMapper` (Faza 5).
 */
@Serializable
data class GatewayConfigDto(val instanceId: String, val logLevel: String)

@Serializable
data class RoutingRuleConditionsConfigDto(val sourceChannel: String? = null, val destinationChannel: String? = null)

@Serializable
data class RoutingRuleConfigDto(
    val ruleId: String,
    val priority: Int,
    val enabled: Boolean = true,
    val conditions: RoutingRuleConditionsConfigDto = RoutingRuleConditionsConfigDto(),
    val targetChannel: String,
    val targetAdapter: String,
    val deliveryPolicy: String,
    val setPriority: String? = null
)

@Serializable
data class RoutingConfigDto(val rules: List<RoutingRuleConfigDto> = emptyList())

@Serializable
data class AdapterConfigEntryDto(
    val adapterId: String,
    val type: String,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class SchedulerTaskConfigDto(val taskId: String, val intervalSeconds: Int)

@Serializable
data class SchedulerConfigDto(val tasks: List<SchedulerTaskConfigDto> = emptyList())

@Serializable
data class SecurityConfigDto(val secretStore: String)

@Serializable
data class MonitoringConfigDto(val healthCheckIntervalSeconds: Int)

@Serializable
data class MessageStoreConfigDto(val retentionDays: Int, val deduplicationRetentionDays: Int = 365)

@Serializable
data class NotificationChannelConfigDto(
    val channelId: String,
    val type: String,
    val address: String? = null,
    val url: String? = null
)

@Serializable
data class NotificationRoutingConfigDto(
    val level: String,
    val channels: List<String>,
    val escalateAfterMinutes: Int? = null
)

@Serializable
data class NotificationsConfigDto(
    val channels: List<NotificationChannelConfigDto> = emptyList(),
    val routing: List<NotificationRoutingConfigDto> = emptyList()
)

@Serializable
data class ConfigurationDocumentDto(
    val version: String,
    val gateway: GatewayConfigDto,
    val routing: RoutingConfigDto,
    val adapters: List<AdapterConfigEntryDto>,
    val scheduler: SchedulerConfigDto,
    val security: SecurityConfigDto,
    val monitoring: MonitoringConfigDto,
    val messageStore: MessageStoreConfigDto,
    val notifications: NotificationsConfigDto
)

fun ConfigurationDocument.toDto(): ConfigurationDocumentDto = ConfigurationDocumentDto(
    version = version,
    gateway = GatewayConfigDto(gateway.instanceId, gateway.logLevel),
    routing = RoutingConfigDto(
        routing.rules.map {
            RoutingRuleConfigDto(
                ruleId = it.ruleId,
                priority = it.priority,
                enabled = it.enabled,
                conditions = RoutingRuleConditionsConfigDto(it.conditions.sourceChannel, it.conditions.destinationChannel),
                targetChannel = it.targetChannel,
                targetAdapter = it.targetAdapter,
                deliveryPolicy = it.deliveryPolicy,
                setPriority = it.setPriority
            )
        }
    ),
    adapters = adapters.map { AdapterConfigEntryDto(it.adapterId, it.type, it.enabled, it.config) },
    scheduler = SchedulerConfigDto(scheduler.tasks.map { SchedulerTaskConfigDto(it.taskId, it.intervalSeconds) }),
    security = SecurityConfigDto(security.secretStore),
    monitoring = MonitoringConfigDto(monitoring.healthCheckIntervalSeconds),
    messageStore = MessageStoreConfigDto(messageStore.retentionDays, messageStore.deduplicationRetentionDays),
    notifications = NotificationsConfigDto(
        notifications.channels.map { NotificationChannelConfigDto(it.channelId, it.type, it.address, it.url) },
        notifications.routing.map { NotificationRoutingConfigDto(it.level, it.channels, it.escalateAfterMinutes) }
    )
)

fun ConfigurationDocumentDto.toDomain(): ConfigurationDocument = ConfigurationDocument(
    version = version,
    gateway = GatewayConfig(gateway.instanceId, gateway.logLevel),
    routing = RoutingConfig(
        routing.rules.map {
            RoutingRuleConfig(
                ruleId = it.ruleId,
                priority = it.priority,
                enabled = it.enabled,
                conditions = RoutingRuleConditionsConfig(it.conditions.sourceChannel, it.conditions.destinationChannel),
                targetChannel = it.targetChannel,
                targetAdapter = it.targetAdapter,
                deliveryPolicy = it.deliveryPolicy,
                setPriority = it.setPriority
            )
        }
    ),
    adapters = adapters.map { AdapterConfigEntry(it.adapterId, it.type, it.enabled, it.config) },
    scheduler = SchedulerConfig(scheduler.tasks.map { SchedulerTaskConfig(it.taskId, it.intervalSeconds) }),
    security = SecurityConfig(security.secretStore),
    monitoring = MonitoringConfig(monitoring.healthCheckIntervalSeconds),
    messageStore = MessageStoreConfig(messageStore.retentionDays, messageStore.deduplicationRetentionDays),
    notifications = NotificationsConfig(
        notifications.channels.map { NotificationChannelConfig(it.channelId, it.type, it.address, it.url) },
        notifications.routing.map { NotificationRoutingConfig(it.level, it.channels, it.escalateAfterMinutes) }
    )
)
