package midomail.adapter.gsm

import midomail.domain.port.memory.InMemoryAttachmentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MmsMessageMapperTest {

    private val attachmentStore = InMemoryAttachmentStore()
    private val mapper = MmsMessageMapper(attachmentStore)

    @Test
    fun `text part becomes the payload content, source is the sender, destination is unknown`() {
        val mms = MmsMessage(
            sender = "+48123456789",
            timestampMillis = 1_000L,
            parts = listOf(MmsPart(contentType = "text/plain", fileName = null, bytes = "hello".toByteArray()))
        )

        val message = mapper.fromMms(mms)

        assertEquals("hello", message.payload.content)
        assertEquals("+48123456789", message.source.address)
        assertEquals("mms", message.source.type.value)
        assertEquals(null, message.destination.address)
        assertEquals("mms", message.destination.type.value)
        assertTrue(message.payload.attachments.isEmpty())
    }

    @Test
    fun `multiple text parts are concatenated in order`() {
        val mms = MmsMessage(
            sender = "+48123456789",
            timestampMillis = 1_000L,
            parts = listOf(
                MmsPart(contentType = "text/plain", fileName = null, bytes = "part one, ".toByteArray()),
                MmsPart(contentType = "text/plain; charset=utf-8", fileName = null, bytes = "part two".toByteArray())
            )
        )

        val message = mapper.fromMms(mms)

        assertEquals("part one, part two", message.payload.content)
    }

    @Test
    fun `non-text parts become attachments written to the AttachmentStore`() {
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val mms = MmsMessage(
            sender = "+48123456789",
            timestampMillis = 1_000L,
            parts = listOf(
                MmsPart(contentType = "text/plain", fileName = null, bytes = "zdjęcie:".toByteArray()),
                MmsPart(contentType = "image/jpeg", fileName = "photo.jpg", bytes = imageBytes)
            )
        )

        val message = mapper.fromMms(mms)

        val attachment = message.payload.attachments.single()
        assertEquals("image/jpeg", attachment.contentType)
        assertEquals("photo.jpg", attachment.fileName)
        assertEquals(4L, attachment.size)
        assertTrue(attachmentStore.read(attachment.dataReference).contentEquals(imageBytes))
    }

    @Test
    fun `a missing file name defaults to a generic name`() {
        val mms = MmsMessage(
            sender = "+48123456789",
            timestampMillis = 1_000L,
            parts = listOf(MmsPart(contentType = "image/png", fileName = null, bytes = byteArrayOf(9)))
        )

        val message = mapper.fromMms(mms)

        assertEquals("attachment", message.payload.attachments.single().fileName)
    }

    @Test
    fun `application-smil layout parts are not treated as attachments`() {
        val mms = MmsMessage(
            sender = "+48123456789",
            timestampMillis = 1_000L,
            parts = listOf(
                MmsPart(contentType = "application/smil", fileName = "layout.smil", bytes = byteArrayOf(1)),
                MmsPart(contentType = "text/plain", fileName = null, bytes = "treść".toByteArray())
            )
        )

        val message = mapper.fromMms(mms)

        assertTrue(message.payload.attachments.isEmpty())
        assertEquals("treść", message.payload.content)
    }

    @Test
    fun `external reference is deterministic and ignores attachment bytes`() {
        val first = mapper.fromMms(
            MmsMessage(
                sender = "+48123456789",
                timestampMillis = 1_000L,
                parts = listOf(
                    MmsPart(contentType = "text/plain", fileName = null, bytes = "treść".toByteArray()),
                    MmsPart(contentType = "image/jpeg", fileName = "a.jpg", bytes = byteArrayOf(1, 2, 3))
                )
            )
        )
        val second = mapper.fromMms(
            MmsMessage(
                sender = "+48123456789",
                timestampMillis = 1_000L,
                parts = listOf(
                    MmsPart(contentType = "text/plain", fileName = null, bytes = "treść".toByteArray()),
                    MmsPart(contentType = "image/jpeg", fileName = "b.jpg", bytes = byteArrayOf(9, 9, 9, 9))
                )
            )
        )

        assertEquals(first.identity.externalReference, second.identity.externalReference)
    }

    @Test
    fun `each mapped message is the root of its own new thread`() {
        val message = mapper.fromMms(
            MmsMessage(
                sender = "+48123456789",
                timestampMillis = 1_000L,
                parts = listOf(MmsPart(contentType = "text/plain", fileName = null, bytes = "x".toByteArray()))
            )
        )

        assertEquals(null, message.identity.causationId)
    }

    @Test
    fun `a configured forwardToAddress becomes the destination address`() {
        val forwardingMapper = MmsMessageMapper(attachmentStore, forwardToAddress = "gateway@example.com")

        val message = forwardingMapper.fromMms(
            MmsMessage(
                sender = "+48123456789",
                timestampMillis = 1_000L,
                parts = listOf(MmsPart(contentType = "text/plain", fileName = null, bytes = "x".toByteArray()))
            )
        )

        assertEquals("gateway@example.com", message.destination.address)
    }
}
