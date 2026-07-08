package midomail.domain.message

import midomail.domain.processing.ProcessingContext

/**
 * Kanoniczny model komunikatu wykorzystywany przez Communication Gateway
 * (10-Core/12-GatewayMessage.md; SPEC-0001-GatewayMessage.md).
 *
 * Niezależny od transportu, platformy i technologii implementacji. Każdy adapter tłumaczy
 * własny format na GatewayMessage przed przekazaniem go do Gateway Engine oraz odwrotnie.
 * Niemutowalny — zmiana stanu przetwarzania (Gateway Engine) tworzy nową kopię przez [copy].
 */
data class GatewayMessage(
    val identity: Identity,
    val source: Channel,
    val destination: Channel,
    val payload: Payload,
    val attributes: Map<String, String> = emptyMap(),
    val processingContext: ProcessingContext = ProcessingContext()
)
