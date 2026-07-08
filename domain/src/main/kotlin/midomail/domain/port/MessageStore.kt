package midomail.domain.port

import midomail.domain.adapter.AdapterId
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.MessageId
import midomail.domain.message.MessagePriority
import midomail.domain.processing.ProcessingState
import java.time.Instant

/** Wynik `insertIfAbsent` (SPEC-0009-Message-Store-Contract.md, §Atomowość i Exactly Once). */
enum class InsertResult {
    INSERTED,
    ALREADY_EXISTS
}

enum class MessageSortField {
    CREATED_AT,
    UPDATED_AT,
    MESSAGE_PRIORITY
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

/** Domyślnie malejąco po CreatedAt (SPEC-0009-Message-Store-Contract.md, §Sortowanie). */
data class MessageSort(
    val field: MessageSortField = MessageSortField.CREATED_AT,
    val direction: SortDirection = SortDirection.DESCENDING
)

/**
 * Filtry zapytania (SPEC-0009-Message-Store-Contract.md, §Filtry). `contentSearch` odpowiada
 * pełnotekstowemu wyszukiwaniu w `Payload.Content`.
 *
 * [channelAddress] — dopasowuje `Channel.address` źródła LUB celu (np. numer telefonu, adres
 * e-mail). Amendment addytywny — wprowadzony, by SMS (protokół bez natywnego pojęcia
 * „odpowiedzi"/wątkowania, w przeciwieństwie do e-maila) mógł odnaleźć swoją istniejącą
 * korelację po numerze nadawcy (ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md) — istniejące
 * wywołania bez zmian dzięki wartości domyślnej `null`.
 */
data class MessageQueryFilter(
    val channelType: ChannelType? = null,
    val adapterId: AdapterId? = null,
    val processingState: ProcessingState? = null,
    val messagePriority: MessagePriority? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
    val correlationId: CorrelationId? = null,
    val externalReference: ExternalReference? = null,
    val contentSearch: String? = null,
    val channelAddress: String? = null
)

/** Paginacja kursorowa, nie offsetowa (SPEC-0009-Message-Store-Contract.md, §Paginacja). */
data class PageRequest(
    val cursor: String? = null,
    val size: Int = 50
)

data class Page<T>(
    val items: List<T>,
    val nextCursor: String?
)

/**
 * Metadane przechowywania (62-Komunikaty.md §2 „Czas utworzenia"/„Ostatnia aktualizacja") —
 * śledzone wewnętrznie przez implementacje `MessageStore` od Fazy 1 (widoczne pośrednio przez
 * `MessageSort.CREATED_AT`/`UPDATED_AT`), ale nigdy nie wystawione per-komunikat. Amendment
 * addytywny (Iteracja 6.23) — istniejące sygnatury bez zmian.
 */
data class MessageMetadata(val createdAt: Instant, val updatedAt: Instant)

/**
 * Port Message Store — jedyne miejsce trwałego przechowywania GatewayMessage
 * (10-Core/18-Porty.md; SPEC-0009-Message-Store-Contract.md).
 *
 * [insertIfAbsent] i [compareAndSet] muszą być atomowe (SPEC-0009, §Atomowość i Exactly Once) —
 * to bezpośrednie zabezpieczenie przed błędem odtworzonym w wersji 1.x, gdzie sprawdzenie
 * istnienia i zapis były rozdzielone i nieatomowe.
 */
interface MessageStore {

    fun findById(messageId: MessageId): GatewayMessage?

    /** Wykorzystywane przez Exactly Once przed utworzeniem nowego GatewayMessage. */
    fun findByExternalReference(externalReference: ExternalReference): GatewayMessage?

    /** Pełna historia jednego procesu biznesowego. */
    fun findByCorrelationId(correlationId: CorrelationId): List<GatewayMessage>

    fun query(filter: MessageQueryFilter, sort: MessageSort, page: PageRequest): Page<GatewayMessage>

    /**
     * Atomowo rejestruje komunikat, jeśli [externalReference] nie był jeszcze widziany.
     * Zwraca [InsertResult.INSERTED] albo [InsertResult.ALREADY_EXISTS] — sprawdzenie i zapis
     * jako jedna, niepodzielna operacja.
     */
    fun insertIfAbsent(externalReference: ExternalReference, message: GatewayMessage): InsertResult

    /**
     * Atomowa aktualizacja (compare-and-set) — podmienia zapisaną wartość na [updated] wyłącznie
     * jeśli aktualnie zapisana wartość dla [messageId] jest równa [expected]. Zwraca `false`, jeśli
     * zapisana wartość już się zmieniła (zapobiega utracie równoległych aktualizacji stanu).
     */
    fun compareAndSet(messageId: MessageId, expected: GatewayMessage, updated: GatewayMessage): Boolean

    /**
     * Usuwa wyłącznie wpis deduplikacji `externalReference→messageId` (ADR-0028-Message-Reprocessing.md,
     * SPEC-0008 §Ręczne ponowne przetworzenie) — NIE usuwa oryginalnego rekordu `GatewayMessage`
     * (nadal dostępny przez [findById]/[findByExternalReference] po ponownym wstawieniu pod nowym
     * [MessageId]). Kolejne [insertIfAbsent] dla tego [externalReference] powiedzie się ponownie.
     * Operacja administracyjna, jawnie audytowana na warstwie Admin API — nie automatyczna ścieżka
     * Exactly Once.
     */
    fun invalidateDeduplication(externalReference: ExternalReference)

    /** `null` jeśli [messageId] nie istnieje. */
    fun metadataFor(messageId: MessageId): MessageMetadata?
}
