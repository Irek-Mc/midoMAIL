package midomail.adapter.rest.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testy round-trip serializacji (ADR-0024-Biblioteka-JSON.md) — każdy DTO z SPEC-0024 musi
 * serializować się do JSON i deserializować z powrotem do identycznej wartości.
 */
class DtoSerializationTest {

    private val json = Json

    @Test
    fun `AdapterSummaryDto round-trips through JSON`() {
        val dto = AdapterSummaryDto(
            adapterId = "email-primary",
            adapterVersion = "1.0",
            state = "READY",
            channels = listOf("email"),
            capabilities = listOf("SUPPORTS_ATTACHMENTS"),
            healthy = true,
            healthDetails = null,
            messagesSent = 42,
            messagesReceived = 7,
            errorCount = 0,
            throttledCount = 0
        )

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<AdapterSummaryDto>(encoded)

        assertEquals(dto, decoded)
    }

    @Test
    fun `RoutingRuleDto round-trips through JSON, including nested RoutingConditionsDto`() {
        val dto = RoutingRuleDto(
            ruleId = "sms-to-email",
            priority = 100,
            enabled = true,
            conditions = RoutingConditionsDto(sourceChannel = "sms", destinationChannel = null),
            targetChannel = "email",
            targetAdapter = "email-primary",
            deliveryPolicy = "AT_LEAST_ONCE",
            setPriority = null,
            version = "1"
        )

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<RoutingRuleDto>(encoded)

        assertEquals(dto, decoded)
    }

    @Test
    fun `ConfigEntryDto round-trips through JSON, including a non-empty history list`() {
        val dto = ConfigEntryDto(
            key = "gateway.instanceId",
            value = "midomail-01",
            history = listOf("midomail-00", "midomail-original")
        )

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<ConfigEntryDto>(encoded)

        assertEquals(dto, decoded)
    }

    @Test
    fun `ConfigEntryDto with a null value serializes and deserializes correctly`() {
        val dto = ConfigEntryDto(key = "unknown.key", value = null, history = emptyList())

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<ConfigEntryDto>(encoded)

        assertEquals(dto, decoded)
    }

    @Test
    fun `MetricsSnapshotDto round-trips through JSON`() {
        val dto = MetricsSnapshotDto(
            adapterId = "email-primary",
            periodStart = "2026-07-06T10:00:00Z",
            periodEnd = "2026-07-06T10:05:00Z",
            messagesSent = 3,
            messagesReceived = 1,
            errorCount = 0,
            throttledCount = 0
        )

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<MetricsSnapshotDto>(encoded)

        assertEquals(dto, decoded)
    }

    @Test
    fun `MessageTraceDto round-trips through JSON, including nested EventDto list`() {
        val dto = MessageTraceDto(
            correlationId = "correlation-1",
            messageIds = listOf("message-1"),
            events = listOf(
                EventDto(
                    eventId = "event-1",
                    eventType = "domain.message_routed",
                    category = "DOMAIN",
                    timestamp = "2026-07-06T10:00:00Z",
                    correlationId = "correlation-1"
                )
            )
        )

        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<MessageTraceDto>(encoded)

        assertEquals(dto, decoded)
    }
}
