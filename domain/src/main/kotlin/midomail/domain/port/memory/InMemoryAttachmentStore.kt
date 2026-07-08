package midomail.domain.port.memory

import midomail.domain.message.DataReference
import midomail.domain.port.AttachmentStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Referencyjna implementacja [AttachmentStore] w pamięci (ADR-0013-Attachment-Store.md;
 * SPEC-0016-Attachment-Store-Contract.md) — dane nie przeżywają restartu procesu.
 */
class InMemoryAttachmentStore : AttachmentStore {

    private val data = ConcurrentHashMap<DataReference, ByteArray>()

    override fun write(data: ByteArray): DataReference {
        val reference = DataReference(UUID.randomUUID().toString())
        this.data[reference] = data
        return reference
    }

    override fun read(reference: DataReference): ByteArray =
        data[reference] ?: throw NoSuchElementException("Brak danych dla DataReference: ${reference.value}")
}
