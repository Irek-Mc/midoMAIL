package midomail.domain.port.memory

import midomail.domain.adapter.AdapterId
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.MessagePriority
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.InsertResult
import midomail.domain.port.MessageQueryFilter
import midomail.domain.port.MessageSort
import midomail.domain.port.MessageSortField
import midomail.domain.port.PageRequest
import midomail.domain.port.SortDirection
import midomail.domain.processing.ProcessingContext
import midomail.domain.processing.ProcessingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza pełny kontrakt Message Store z SPEC-0009-Message-Store-Contract.md przez
 * referencyjną implementację [InMemoryMessageStore].
 */
class InMemoryMessageStoreTest {

    private fun createMessage(
        messageId: String = "message-1",
        externalReference: String = "<ext-1@localhost>",
        correlationId: String = "correlation-1",
        sourceType: String = "gsm",
        destinationType: String = "email",
        sourceAddress: String? = null,
        destinationAddress: String? = null,
        adapterId: String? = null,
        messagePriority: MessagePriority = MessagePriority.NORMAL,
        processingState: ProcessingState = ProcessingState.ACCEPTED,
        content: String = "Treść testowa"
    ): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId(messageId),
            correlationId = CorrelationId(correlationId),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference(externalReference),
            messagePriority = messagePriority
        ),
        source = Channel(type = ChannelType(sourceType), address = sourceAddress, adapterId = adapterId?.let { AdapterId(it) }),
        destination = Channel(type = ChannelType(destinationType), address = destinationAddress),
        payload = Payload(content = content),
        processingContext = ProcessingContext(processingState)
    )

    // --- insertIfAbsent / findByExternalReference / findById ---

    @Test
    fun `insertIfAbsent returns INSERTED for a new ExternalReference`() {
        val store = InMemoryMessageStore()

        val result = store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), createMessage())

        assertEquals(InsertResult.INSERTED, result)
    }

    @Test
    fun `insertIfAbsent returns ALREADY_EXISTS for a duplicate ExternalReference`() {
        val store = InMemoryMessageStore()
        val externalReference = ExternalReference("<ext-1@localhost>")
        store.insertIfAbsent(externalReference, createMessage())

        val result = store.insertIfAbsent(externalReference, createMessage(messageId = "message-2"))

        assertEquals(InsertResult.ALREADY_EXISTS, result)
    }

    @Test
    fun `findById returns the inserted message`() {
        val store = InMemoryMessageStore()
        val message = createMessage()
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), message)

        assertEquals(message, store.findById(MessageId("message-1")))
    }

    @Test
    fun `findById returns null for an unknown MessageId`() {
        val store = InMemoryMessageStore()

        assertNull(store.findById(MessageId("unknown")))
    }

    @Test
    fun `findByExternalReference returns the associated message`() {
        val store = InMemoryMessageStore()
        val message = createMessage()
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), message)

        assertEquals(message, store.findByExternalReference(ExternalReference("<ext-1@localhost>")))
    }

    @Test
    fun `findByCorrelationId returns all messages sharing the same CorrelationId`() {
        val store = InMemoryMessageStore()
        val first = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", correlationId = "shared")
        val second = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", correlationId = "shared")
        val unrelated = createMessage(messageId = "message-3", externalReference = "<ext-3@localhost>", correlationId = "other")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), first)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), second)
        store.insertIfAbsent(ExternalReference("<ext-3@localhost>"), unrelated)

        val history = store.findByCorrelationId(CorrelationId("shared"))

        assertEquals(setOf(first, second), history.toSet())
    }

    // --- compareAndSet ---

    @Test
    fun `compareAndSet succeeds when expected matches the currently stored value`() {
        val store = InMemoryMessageStore()
        val original = createMessage(processingState = ProcessingState.ACCEPTED)
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), original)
        val advanced = original.copy(processingContext = ProcessingContext(ProcessingState.VALIDATED))

        val succeeded = store.compareAndSet(MessageId("message-1"), original, advanced)

        assertTrue(succeeded)
        assertEquals(advanced, store.findById(MessageId("message-1")))
    }

    @Test
    fun `compareAndSet fails when expected does not match the currently stored value - prevents lost updates`() {
        val store = InMemoryMessageStore()
        val original = createMessage(processingState = ProcessingState.ACCEPTED)
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), original)
        val staleExpected = original.copy(processingContext = ProcessingContext(ProcessingState.SCHEDULED))
        val attemptedUpdate = original.copy(processingContext = ProcessingContext(ProcessingState.VALIDATED))

        val succeeded = store.compareAndSet(MessageId("message-1"), staleExpected, attemptedUpdate)

        assertFalse(succeeded)
        assertEquals(original, store.findById(MessageId("message-1")))
    }

    // --- query: filtry ---

    @Test
    fun `query filters by channel type`() {
        val store = InMemoryMessageStore()
        val gsmMessage = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", sourceType = "gsm")
        val restMessage = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", sourceType = "rest")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), gsmMessage)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), restMessage)

        val page = store.query(
            filter = MessageQueryFilter(channelType = ChannelType("gsm")),
            sort = MessageSort(),
            page = PageRequest()
        )

        assertEquals(listOf(gsmMessage), page.items)
    }

    @Test
    fun `query filters by processing state`() {
        val store = InMemoryMessageStore()
        val accepted = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", processingState = ProcessingState.ACCEPTED)
        val failed = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", processingState = ProcessingState.FAILED)
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), accepted)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), failed)

        val page = store.query(
            filter = MessageQueryFilter(processingState = ProcessingState.FAILED),
            sort = MessageSort(),
            page = PageRequest()
        )

        assertEquals(listOf(failed), page.items)
    }

    @Test
    fun `query full-text searches Payload Content case-insensitively`() {
        val store = InMemoryMessageStore()
        val match = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", content = "Zawiera SZUKANE słowo")
        val noMatch = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", content = "Inna treść")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), match)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), noMatch)

        val page = store.query(
            filter = MessageQueryFilter(contentSearch = "szukane"),
            sort = MessageSort(),
            page = PageRequest()
        )

        assertEquals(listOf(match), page.items)
    }

    @Test
    fun `query filters by adapterId on either source or destination`() {
        val store = InMemoryMessageStore()
        val withAdapter = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", adapterId = "gsm-primary")
        val withoutAdapter = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", adapterId = null)
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), withAdapter)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), withoutAdapter)

        val page = store.query(
            filter = MessageQueryFilter(adapterId = AdapterId("gsm-primary")),
            sort = MessageSort(),
            page = PageRequest()
        )

        assertEquals(listOf(withAdapter), page.items)
    }

    /**
     * ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md - filtr wprowadzony, by SMS mógł
     * odnaleźć swoją istniejącą korelację po numerze nadawcy (SMS nie niesie In-Reply-To/References
     * jak e-mail).
     */
    @Test
    fun `query filters by channelAddress on either source or destination`() {
        val store = InMemoryMessageStore()
        val fromThisNumber = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", sourceAddress = "+48123456789")
        val toThisNumber = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", destinationAddress = "+48123456789")
        val unrelated = createMessage(messageId = "message-3", externalReference = "<ext-3@localhost>", sourceAddress = "+48999999999")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), fromThisNumber)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), toThisNumber)
        store.insertIfAbsent(ExternalReference("<ext-3@localhost>"), unrelated)

        val page = store.query(
            filter = MessageQueryFilter(channelAddress = "+48123456789"),
            sort = MessageSort(),
            page = PageRequest()
        )

        assertEquals(setOf(fromThisNumber, toThisNumber), page.items.toSet())
    }

    // --- paginacja kursorowa ---

    @Test
    fun `cursor pagination returns all items across pages without duplicates or omissions`() {
        val store = InMemoryMessageStore()
        (1..5).forEach { index ->
            store.insertIfAbsent(
                ExternalReference("<ext-$index@localhost>"),
                createMessage(messageId = "message-$index", externalReference = "<ext-$index@localhost>")
            )
        }

        val firstPage = store.query(MessageQueryFilter(), MessageSort(), PageRequest(size = 2))
        assertEquals(2, firstPage.items.size)
        assertTrue(firstPage.nextCursor != null)

        val secondPage = store.query(MessageQueryFilter(), MessageSort(), PageRequest(cursor = firstPage.nextCursor, size = 2))
        assertEquals(2, secondPage.items.size)
        assertTrue(secondPage.nextCursor != null)

        val thirdPage = store.query(MessageQueryFilter(), MessageSort(), PageRequest(cursor = secondPage.nextCursor, size = 2))
        assertEquals(1, thirdPage.items.size)
        assertNull(thirdPage.nextCursor)

        val allMessageIds = (firstPage.items + secondPage.items + thirdPage.items).map { it.identity.messageId }
        assertEquals((1..5).map { MessageId("message-$it") }.toSet(), allMessageIds.toSet())
    }

    @Test
    fun `cursor pagination remains stable when a new record is inserted between page reads`() {
        val store = InMemoryMessageStore()
        (1..3).forEach { index ->
            store.insertIfAbsent(
                ExternalReference("<ext-$index@localhost>"),
                createMessage(messageId = "message-$index", externalReference = "<ext-$index@localhost>")
            )
        }

        val firstPage = store.query(MessageQueryFilter(), MessageSort(), PageRequest(size = 2))

        // Nowy rekord dopisany między odczytami stron — paginacja oparta o kursor
        // (CreatedAt, MessageId) nie powinna gubić ani duplikować już odwiedzonych elementów.
        store.insertIfAbsent(
            ExternalReference("<ext-new@localhost>"),
            createMessage(messageId = "message-new", externalReference = "<ext-new@localhost>")
        )

        val secondPage = store.query(MessageQueryFilter(), MessageSort(), PageRequest(cursor = firstPage.nextCursor, size = 10))

        val firstPageIds = firstPage.items.map { it.identity.messageId }.toSet()
        val secondPageIds = secondPage.items.map { it.identity.messageId }.toSet()
        assertTrue(firstPageIds.intersect(secondPageIds).isEmpty(), "Strony nie powinny się nakładać")
    }

    // --- sortowanie po MessagePriority ---

    @Test
    fun `query can sort by MessagePriority descending`() {
        val store = InMemoryMessageStore()
        val low = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>", messagePriority = MessagePriority.LOW)
        val critical = createMessage(messageId = "message-2", externalReference = "<ext-2@localhost>", messagePriority = MessagePriority.CRITICAL)
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), low)
        store.insertIfAbsent(ExternalReference("<ext-2@localhost>"), critical)

        val page = store.query(
            filter = MessageQueryFilter(),
            sort = MessageSort(field = MessageSortField.MESSAGE_PRIORITY, direction = SortDirection.DESCENDING),
            page = PageRequest()
        )

        assertEquals(listOf(critical, low), page.items)
    }

    // --- invalidateDeduplication (ADR-0028) ---

    @Test
    fun `invalidateDeduplication allows insertIfAbsent to succeed again for the same ExternalReference`() {
        val store = InMemoryMessageStore()
        val original = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), original)

        store.invalidateDeduplication(ExternalReference("<ext-1@localhost>"))
        val retried = createMessage(messageId = "message-2", externalReference = "<ext-1@localhost>")
        val result = store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), retried)

        assertEquals(InsertResult.INSERTED, result)
    }

    @Test
    fun `invalidateDeduplication does not remove the original message record`() {
        val store = InMemoryMessageStore()
        val original = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), original)

        store.invalidateDeduplication(ExternalReference("<ext-1@localhost>"))

        assertEquals(original, store.findById(MessageId("message-1")))
    }

    @Test
    fun `invalidateDeduplication on an unknown ExternalReference has no effect`() {
        val store = InMemoryMessageStore()

        store.invalidateDeduplication(ExternalReference("<unknown@localhost>"))

        assertNull(store.findByExternalReference(ExternalReference("<unknown@localhost>")))
    }

    // --- metadataFor() (Iteracja 6.23) ---

    @Test
    fun `metadataFor returns createdAt and updatedAt for an existing message`() {
        val store = InMemoryMessageStore()
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>"))

        val metadata = store.metadataFor(MessageId("message-1"))

        assertTrue(metadata != null)
        assertEquals(metadata!!.createdAt, metadata.updatedAt, "brak aktualizacji od zapisu - createdAt == updatedAt")
    }

    @Test
    fun `metadataFor updatedAt changes after compareAndSet, createdAt stays the same`() {
        val store = InMemoryMessageStore()
        val original = createMessage(messageId = "message-1", externalReference = "<ext-1@localhost>")
        store.insertIfAbsent(ExternalReference("<ext-1@localhost>"), original)
        val beforeUpdate = store.metadataFor(MessageId("message-1"))!!

        Thread.sleep(5)
        store.compareAndSet(MessageId("message-1"), original, original.copy(payload = original.payload.copy(content = "zaktualizowana treść")))
        val afterUpdate = store.metadataFor(MessageId("message-1"))!!

        assertEquals(beforeUpdate.createdAt, afterUpdate.createdAt)
        assertTrue(afterUpdate.updatedAt.isAfter(beforeUpdate.updatedAt))
    }

    @Test
    fun `metadataFor returns null for an unknown MessageId`() {
        val store = InMemoryMessageStore()

        assertNull(store.metadataFor(MessageId("unknown")))
    }
}
