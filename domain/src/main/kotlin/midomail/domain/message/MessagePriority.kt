package midomail.domain.message

/**
 * Priorytet komunikatu (SPEC-0001-GatewayMessage.md, §MessagePriority; ADR-0005-Message-Priority.md).
 *
 * Odrębne od `Priority` reguły routingu (SPEC-0007-Routing-Contract.md) — [MessagePriority]
 * opisuje priorytet samego komunikatu, nie kolejność ewaluacji reguł.
 */
enum class MessagePriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
