package midomail.domain.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt Payload/Attachment z SPEC-0001-GatewayMessage.md, §Payload i
 * 10-Core/12-GatewayMessage.md, §8.
 */
class PayloadTest {

    @Test
    fun `attachments defaults to empty list for purely textual messages`() {
        val payload = Payload(content = "Treść SMS")

        assertTrue(payload.attachments.isEmpty())
    }

    @Test
    fun `payload can carry a non-empty list of attachments`() {
        val attachment = Attachment(
            contentType = "image/jpeg",
            fileName = "zdjecie.jpg",
            size = 204_800,
            dataReference = DataReference("mms-part-1")
        )

        val payload = Payload(content = "Zobacz zdjęcie", attachments = listOf(attachment))

        assertEquals(1, payload.attachments.size)
        assertEquals(attachment, payload.attachments.single())
    }

    @Test
    fun `DataReference rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { DataReference("") }
        assertFailsWith<IllegalArgumentException> { DataReference("   ") }
    }

    @Test
    fun `Attachment stores contentType fileName size and dataReference exactly as provided`() {
        val attachment = Attachment(
            contentType = "application/pdf",
            fileName = "dokument.pdf",
            size = 1_048_576,
            dataReference = DataReference("attachment-ref-42")
        )

        assertEquals("application/pdf", attachment.contentType)
        assertEquals("dokument.pdf", attachment.fileName)
        assertEquals(1_048_576, attachment.size)
        assertEquals("attachment-ref-42", attachment.dataReference.value)
    }
}
