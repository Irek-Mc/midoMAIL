package midomail.domain.message

/**
 * Globalnie unikalny identyfikator komunikatu wewnątrz Communication Gateway
 * (10-Core/12-GatewayMessage.md, §4; SPEC-0001-GatewayMessage.md, §Identity).
 */
@JvmInline
value class MessageId(val value: String) {
    init {
        require(value.isNotBlank()) { "MessageId nie może być pusty" }
    }
}

/** Identyfikator wiążący komunikaty należące do jednego procesu biznesowego. */
@JvmInline
value class CorrelationId(val value: String) {
    init {
        require(value.isNotBlank()) { "CorrelationId nie może być pusty" }
    }
}

/** Identyfikator przyczynowy — opcjonalny (10-Core/12-GatewayMessage.md, §4). */
@JvmInline
value class CausationId(val value: String) {
    init {
        require(value.isNotBlank()) { "CausationId nie może być pusty" }
    }
}

/** Wersja modelu GatewayMessage. */
@JvmInline
value class SchemaVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "SchemaVersion nie może być pusty" }
    }
}

/**
 * Naturalny identyfikator komunikatu w systemie źródłowym, dostarczany przez adapter
 * (10-Core/12-GatewayMessage.md, §5; SPEC-0001-GatewayMessage.md, §Identity).
 *
 * Odrębne od [MessageId] — [MessageId] identyfikuje komunikat wewnątrz Communication Gateway,
 * [ExternalReference] służy do wykrywania duplikatów w ramach Exactly Once przed utworzeniem
 * nowego GatewayMessage.
 */
@JvmInline
value class ExternalReference(val value: String) {
    init {
        require(value.isNotBlank()) { "ExternalReference nie może być pusty" }
    }
}

/**
 * Tożsamość komunikatu — jednoznacznie identyfikuje GatewayMessage w całym cyklu życia systemu
 * (10-Core/12-GatewayMessage.md, §4; SPEC-0001-GatewayMessage.md, §Identity).
 */
data class Identity(
    val messageId: MessageId,
    val correlationId: CorrelationId,
    val causationId: CausationId? = null,
    val schemaVersion: SchemaVersion,
    val externalReference: ExternalReference,
    val messagePriority: MessagePriority = MessagePriority.NORMAL
)
