package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable

/**
 * Symulator routingu (SPEC-0024, §4 Routing — 63-Routing.md §4: „bez wysyłania rzeczywistego
 * komunikatu"). Wyłącznie pola, na podstawie których `RoutingConditions` dopasowuje reguły
 * (ADR-0010-Model-Channel.md) — nie pełny `GatewayMessage`.
 *
 * [SimulateRoutingRequestDto.messagePriority]/[RoutingDecisionDto.messagePriorityBefore]/
 * [RoutingDecisionDto.messagePriorityAfter] dodane w Iteracji 6.24 — 63-Routing.md §4 wymaga
 * pokazania `MessagePriority` przed i po ewaluacji (jeśli reguła zawiera `SetPriority`).
 */
@Serializable
data class SimulateRoutingRequestDto(val sourceChannel: String, val destinationChannel: String, val messagePriority: String = "NORMAL")

@Serializable
data class RoutingDecisionDto(
    val matched: Boolean,
    val targetChannel: String? = null,
    val targetAdapter: String? = null,
    val deliveryPolicy: String? = null,
    val messagePriorityBefore: String? = null,
    val messagePriorityAfter: String? = null
)
