package midomail.domain.routing

import midomail.domain.adapter.AdapterId
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import midomail.domain.message.MessagePriority

/** Wynik ewaluacji Routing Engine (10-Core/13-Routing.md; SPEC-0007-Routing-Contract.md). */
sealed class RoutingDecision {
    data class Routed(
        val targetChannel: ChannelType,
        val targetAdapter: AdapterId,
        val deliveryPolicy: DeliveryPolicy,
        val messagePriority: MessagePriority
    ) : RoutingDecision()

    data object NoMatch : RoutingDecision()
}

/**
 * Silnik ewaluacji reguł routingu (10-Core/13-Routing.md; SPEC-0007-Routing-Contract.md).
 *
 * [rules] musi być podane w kolejności deklaracji — przy równym `priority` wygrywa reguła
 * zadeklarowana wcześniej (SPEC-0005-Configuration-Model.md, §Walidacja krzyżowa: „Wynik routingu
 * jest deterministyczny", 10-Core/13-Routing.md, §3). Sortowanie Kotlina (`sortedByDescending`)
 * jest stabilne, więc zachowuje kolejność deklaracji przy remisie priorytetów.
 *
 * [setPriority] reguły — jeśli obecne — nadpisuje `MessagePriority` komunikatu.
 */
class RoutingEngine(private val rules: List<RoutingRule>) {

    fun route(message: GatewayMessage): RoutingDecision {
        val matchedRule = rules
            .filter { it.enabled }
            .filter { it.conditions.sourceChannel == null || it.conditions.sourceChannel == message.source.type }
            .filter { it.conditions.destinationChannel == null || it.conditions.destinationChannel == message.destination.type }
            .sortedByDescending { it.priority }
            .firstOrNull()

        return matchedRule?.let {
            RoutingDecision.Routed(
                targetChannel = it.targetChannel,
                targetAdapter = it.targetAdapter,
                deliveryPolicy = it.deliveryPolicy,
                messagePriority = it.setPriority ?: message.identity.messagePriority
            )
        } ?: RoutingDecision.NoMatch
    }
}
