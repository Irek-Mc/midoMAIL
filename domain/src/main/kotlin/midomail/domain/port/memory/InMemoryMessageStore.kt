package midomail.domain.port.memory

import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.MessageId
import midomail.domain.port.InsertResult
import midomail.domain.port.MessageMetadata
import midomail.domain.port.MessageQueryFilter
import midomail.domain.port.MessageSort
import midomail.domain.port.MessageSortField
import midomail.domain.port.MessageStore
import midomail.domain.port.Page
import midomail.domain.port.PageRequest
import midomail.domain.port.SortDirection
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private data class StoredRecord(
    val message: GatewayMessage,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Referencyjna implementacja [MessageStore] w pamięci — pełny kontrakt z
 * SPEC-0009-Message-Store-Contract.md, w tym atomowość [insertIfAbsent] i [compareAndSet].
 *
 * CreatedAt/UpdatedAt są metadanymi przechowywania śledzonymi wewnętrznie (SPEC-0009, §Schemat) —
 * nie są polami GatewayMessage. Kursor paginacji jest nieprzezroczystym tokenem kodującym
 * (klucz sortowania, MessageId) jako stabilną parę (SPEC-0009, §Paginacja).
 */
class InMemoryMessageStore : MessageStore {

    private val recordsByMessageId = ConcurrentHashMap<MessageId, StoredRecord>()
    private val messageIdByExternalReference = ConcurrentHashMap<ExternalReference, MessageId>()

    override fun findById(messageId: MessageId): GatewayMessage? =
        recordsByMessageId[messageId]?.message

    override fun findByExternalReference(externalReference: ExternalReference): GatewayMessage? =
        messageIdByExternalReference[externalReference]?.let { recordsByMessageId[it]?.message }

    override fun findByCorrelationId(correlationId: CorrelationId): List<GatewayMessage> =
        recordsByMessageId.values
            .asSequence()
            .map { it.message }
            .filter { it.identity.correlationId == correlationId }
            .toList()

    override fun query(filter: MessageQueryFilter, sort: MessageSort, page: PageRequest): Page<GatewayMessage> {
        val filtered = recordsByMessageId.values.filter { matches(it, filter) }

        val sortKeyOf: (StoredRecord) -> Long = sortKeySelector(sort.field)
        val comparator = compareBy<StoredRecord>({ sortKeyOf(it) }, { it.message.identity.messageId.value })
            .let { if (sort.direction == SortDirection.DESCENDING) it.reversed() else it }

        val sorted = filtered.sortedWith(comparator)

        val afterCursor = page.cursor?.let { decodeCursor(it) }
        val startIndex = if (afterCursor == null) {
            0
        } else {
            sorted.indexOfFirst { record ->
                val key = sortKeyOf(record)
                val idValue = record.message.identity.messageId.value
                val isAfter = if (sort.direction == SortDirection.DESCENDING) {
                    key < afterCursor.first || (key == afterCursor.first && idValue < afterCursor.second)
                } else {
                    key > afterCursor.first || (key == afterCursor.first && idValue > afterCursor.second)
                }
                isAfter
            }.let { if (it == -1) sorted.size else it }
        }

        val pageItems = sorted.drop(startIndex).take(page.size)
        val nextCursor = if (startIndex + page.size < sorted.size) {
            pageItems.lastOrNull()?.let { encodeCursor(sortKeyOf(it), it.message.identity.messageId.value) }
        } else {
            null
        }

        return Page(items = pageItems.map { it.message }, nextCursor = nextCursor)
    }

    override fun insertIfAbsent(externalReference: ExternalReference, message: GatewayMessage): InsertResult {
        var wasInserted = false
        messageIdByExternalReference.computeIfAbsent(externalReference) {
            val now = Instant.now()
            recordsByMessageId[message.identity.messageId] = StoredRecord(message, now, now)
            wasInserted = true
            message.identity.messageId
        }
        return if (wasInserted) InsertResult.INSERTED else InsertResult.ALREADY_EXISTS
    }

    override fun compareAndSet(messageId: MessageId, expected: GatewayMessage, updated: GatewayMessage): Boolean {
        var succeeded = false
        recordsByMessageId.computeIfPresent(messageId) { _, current ->
            if (current.message == expected) {
                succeeded = true
                current.copy(message = updated, updatedAt = Instant.now())
            } else {
                current
            }
        }
        return succeeded
    }

    override fun invalidateDeduplication(externalReference: ExternalReference) {
        messageIdByExternalReference.remove(externalReference)
    }

    override fun metadataFor(messageId: MessageId): MessageMetadata? =
        recordsByMessageId[messageId]?.let { MessageMetadata(it.createdAt, it.updatedAt) }

    private fun matches(record: StoredRecord, filter: MessageQueryFilter): Boolean {
        val message = record.message
        if (filter.channelType != null &&
            message.source.type != filter.channelType &&
            message.destination.type != filter.channelType
        ) {
            return false
        }
        if (filter.adapterId != null &&
            message.source.adapterId != filter.adapterId &&
            message.destination.adapterId != filter.adapterId
        ) {
            return false
        }
        if (filter.processingState != null && message.processingContext.processingState != filter.processingState) {
            return false
        }
        if (filter.messagePriority != null && message.identity.messagePriority != filter.messagePriority) {
            return false
        }
        if (filter.createdAfter != null && record.createdAt.isBefore(filter.createdAfter)) {
            return false
        }
        if (filter.createdBefore != null && record.createdAt.isAfter(filter.createdBefore)) {
            return false
        }
        if (filter.correlationId != null && message.identity.correlationId != filter.correlationId) {
            return false
        }
        if (filter.externalReference != null && message.identity.externalReference != filter.externalReference) {
            return false
        }
        if (filter.contentSearch != null && !message.payload.content.contains(filter.contentSearch, ignoreCase = true)) {
            return false
        }
        if (filter.channelAddress != null &&
            message.source.address != filter.channelAddress &&
            message.destination.address != filter.channelAddress
        ) {
            return false
        }
        return true
    }

    private fun sortKeySelector(field: MessageSortField): (StoredRecord) -> Long = when (field) {
        MessageSortField.CREATED_AT -> { record -> record.createdAt.toEpochMilli() }
        MessageSortField.UPDATED_AT -> { record -> record.updatedAt.toEpochMilli() }
        MessageSortField.MESSAGE_PRIORITY -> { record -> record.message.identity.messagePriority.ordinal.toLong() }
    }

    private fun encodeCursor(sortKey: Long, messageId: String): String =
        Base64.getEncoder().encodeToString("$sortKey|$messageId".toByteArray())

    private fun decodeCursor(cursor: String): Pair<Long, String> {
        val decoded = String(Base64.getDecoder().decode(cursor))
        val separatorIndex = decoded.indexOf('|')
        return decoded.substring(0, separatorIndex).toLong() to decoded.substring(separatorIndex + 1)
    }
}
