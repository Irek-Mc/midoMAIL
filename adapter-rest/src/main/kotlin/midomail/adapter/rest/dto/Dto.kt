package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable

/**
 * DTO warstwy REST (ADR-0024-Biblioteka-JSON.md) — celowo odseparowane od typów domenowych
 * (`:domain` nie zależy od `kotlinx.serialization`). Mapowanie Domain↔DTO jest jawną, ręczną
 * warstwą (Iteracja 5.10+), analogicznie do `EmailMessageMapper` w `:adapter-email`.
 *
 * Kształty pól odpowiadają dosłownie SPEC-0024-Administrative-API-Contract.md, §Kształty
 * żądanie/odpowiedź.
 */
@Serializable
data class AdapterSummaryDto(
    val adapterId: String,
    val adapterVersion: String,
    val state: String,
    val channels: List<String>,
    val capabilities: List<String>,
    val healthy: Boolean,
    val healthDetails: String? = null,
    val messagesSent: Long,
    val messagesReceived: Long,
    val errorCount: Long,
    val throttledCount: Long
)

@Serializable
data class MetricsSnapshotDto(
    val adapterId: String,
    val periodStart: String,
    val periodEnd: String,
    val messagesSent: Long,
    val messagesReceived: Long,
    val errorCount: Long,
    val throttledCount: Long
)

@Serializable
data class EventDto(
    val eventId: String,
    val eventType: String,
    val category: String,
    val timestamp: String,
    val correlationId: String
)

@Serializable
data class MessageTraceDto(
    val correlationId: String,
    val messageIds: List<String>,
    val events: List<EventDto>
)

@Serializable
data class RoutingConditionsDto(
    val sourceChannel: String? = null,
    val destinationChannel: String? = null
)

@Serializable
data class RoutingRuleDto(
    val ruleId: String,
    val priority: Int,
    val enabled: Boolean,
    val conditions: RoutingConditionsDto,
    val targetChannel: String,
    val targetAdapter: String,
    val deliveryPolicy: String,
    val setPriority: String? = null,
    val version: String
)

@Serializable
data class ConfigEntryDto(
    val key: String,
    val value: String?,
    val history: List<String>
)
