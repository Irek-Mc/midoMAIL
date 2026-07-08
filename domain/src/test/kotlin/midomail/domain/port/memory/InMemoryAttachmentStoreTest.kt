package midomail.domain.port.memory

import midomail.domain.message.DataReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * Potwierdza kontrakt AttachmentStore z SPEC-0016-Attachment-Store-Contract.md.
 */
class InMemoryAttachmentStoreTest {

    @Test
    fun `write returns a DataReference that read resolves back to the same bytes`() {
        val store = InMemoryAttachmentStore()
        val originalBytes = byteArrayOf(1, 2, 3, 4, 5)

        val reference = store.write(originalBytes)

        assertContentEquals(originalBytes, store.read(reference))
    }

    @Test
    fun `two writes produce distinct DataReference values`() {
        val store = InMemoryAttachmentStore()

        val first = store.write(byteArrayOf(1))
        val second = store.write(byteArrayOf(2))

        assert(first != second)
    }

    @Test
    fun `reading an unknown DataReference fails`() {
        val store = InMemoryAttachmentStore()

        assertFailsWith<NoSuchElementException> { store.read(DataReference("unknown")) }
    }
}
