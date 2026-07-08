package midomail.domain.message

import midomail.domain.adapter.AdapterId

/**
 * Identyfikator logicznego typu kanału (np. `gsm`, `email`) — jedyne pole [Channel],
 * na podstawie którego Routing Engine podejmuje decyzje (10-Core/12-GatewayMessage.md, §7;
 * ADR-0010-Model-Channel.md).
 */
@JvmInline
value class ChannelType(val value: String) {
    init {
        require(value.isNotBlank()) { "ChannelType nie może być pusty" }
    }
}

/**
 * Model współdzielony przez Source i Destination (10-Core/12-GatewayMessage.md, §7;
 * SPEC-0001-GatewayMessage.md, §Channel; ADR-0010-Model-Channel.md; ADR-0012-Channel-AdapterId.md).
 *
 * [address] jest nieinterpretowany przez Core — Gateway Engine i Routing Engine nigdy nie
 * odczytują ani nie walidują jego wartości, wyłącznie przenoszą do skonsumowania przez adapter.
 * Nigdy nie jest warunkiem reguły routingu (tylko [type] może nim być).
 *
 * [adapterId] jest znany od razu dla Source (adapter, który odebrał komunikat); dla Destination
 * wypełniany dopiero wynikiem decyzji Routing Engine (ADR-0012-Channel-AdapterId.md).
 */
data class Channel(
    val type: ChannelType,
    val address: String? = null,
    val adapterId: AdapterId? = null
)
