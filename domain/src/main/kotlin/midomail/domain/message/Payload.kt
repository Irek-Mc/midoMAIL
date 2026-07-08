package midomail.domain.message

/**
 * Referencja do danych binarnych załącznika — Gateway nie przechowuje trwale samych danych
 * poza czasem przetwarzania komunikatu (10-Core/12-GatewayMessage.md, §8; 30-Infrastructure/
 * 31-Bezpieczenstwo.md, zasada minimalizacji danych).
 */
@JvmInline
value class DataReference(val value: String) {
    init {
        require(value.isNotBlank()) { "DataReference nie może być pusty" }
    }
}

/**
 * Załącznik komunikatu (10-Core/12-GatewayMessage.md, §8; SPEC-0001-GatewayMessage.md, §Payload).
 *
 * Gateway nie interpretuje zawartości załącznika — mapowanie i ewentualne transkodowanie
 * leży po stronie adaptera.
 */
data class Attachment(
    val contentType: String,
    val fileName: String,
    val size: Long,
    val dataReference: DataReference
)

/**
 * Ładunek komunikatu — model wieloczęściowy złożony z treści głównej i opcjonalnej listy
 * załączników (10-Core/12-GatewayMessage.md, §8; SPEC-0001-GatewayMessage.md, §Payload).
 *
 * [attachments] jest pusta dla komunikatów czysto tekstowych.
 */
data class Payload(
    val content: String,
    val attachments: List<Attachment> = emptyList()
)
