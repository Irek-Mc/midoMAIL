package midomail.adapter.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMultipart
import midomail.domain.message.Attachment
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryMessageStore
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza mapowanie GatewayMessage → MIME (20-Adapters/22-Adapter-Email.md, §7).
 */
class EmailMessageMapperTest {

    private val session: Session = Session.getInstance(Properties())

    private fun createIdentity(): Identity = Identity(
        messageId = MessageId("message-1"),
        correlationId = CorrelationId("correlation-1"),
        schemaVersion = SchemaVersion("2.0"),
        externalReference = ExternalReference("<ext-1@localhost>")
    )

    @Test
    fun `plain text message maps to a simple MimeMessage`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("email"), address = "sender@example.com"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Treść wiadomości"),
            attributes = mapOf("subject" to "Temat testowy")
        )

        val mimeMessage = mapper.toMime(message, session)

        val fromAddress = mimeMessage.from.single() as jakarta.mail.internet.InternetAddress
        assertEquals("sender@example.com", fromAddress.address)
        assertEquals("midoMAIL Gateway", fromAddress.personal)
        assertEquals("recipient@example.com", mimeMessage.getRecipients(jakarta.mail.Message.RecipientType.TO).single().toString())
        assertEquals("Temat testowy", mimeMessage.subject)
        assertTrue(mimeMessage.content is String)
        assertEquals("Treść wiadomości", mimeMessage.content)
    }

    @Test
    fun `custom fromDisplayName is used as the From personal name instead of the default`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore(), fromDisplayName = "midoMAIL SMS/MMS")
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("sms"), address = "+48123456789"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Tresc SMS")
        )

        val mimeMessage = mapper.toMime(message, session)

        val fromAddress = mimeMessage.from.single() as jakarta.mail.internet.InternetAddress
        assertEquals("+48123456789", fromAddress.address)
        assertEquals("midoMAIL SMS/MMS", fromAddress.personal)
    }

    @Test
    fun `message with an attachment maps to multipart MIME with correct contentType and fileName`() {
        val attachmentStore = InMemoryAttachmentStore()
        val mapper = EmailMessageMapper(attachmentStore)
        val reference = attachmentStore.write(byteArrayOf(1, 2, 3, 4))
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("email"), address = "sender@example.com"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(
                content = "Zdjęcie w załączniku",
                attachments = listOf(
                    Attachment(
                        contentType = "image/jpeg",
                        fileName = "zdjecie.jpg",
                        size = 4,
                        dataReference = reference
                    )
                )
            )
        )

        val mimeMessage = mapper.toMime(message, session)

        assertIs<MimeMultipart>(mimeMessage.content)
        val multipart = mimeMessage.content as MimeMultipart
        assertEquals(2, multipart.count)
        val attachmentPart = multipart.getBodyPart(1)
        assertEquals("zdjecie.jpg", attachmentPart.fileName)
        assertTrue(attachmentPart.contentType.startsWith("image/jpeg"))
    }

    @Test
    fun `message without a subject attribute falls back to the source address as subject - zgloszenie nadzorcy projektu, pusty temat SMS-MMS w skrzynce`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("sms"), address = "+48123456789"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Bez tematu")
        )

        val mimeMessage = mapper.toMime(message, session)

        assertEquals("+48123456789", mimeMessage.subject)
    }

    @Test
    fun `a subject attribute present takes priority over the source address fallback`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("email"), address = "sender@example.com"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Wątek e-mail"),
            attributes = mapOf("subject" to "Re: Temat oryginalny")
        )

        val mimeMessage = mapper.toMime(message, session)

        assertEquals("Re: Temat oryginalny", mimeMessage.subject)
    }

    /**
     * ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md — zgłoszenie nadzorcy projektu: kolejna
     * odpowiedź nadawcy SMS-a wracała jako osobny wątek e-mail zamiast kontynuacji rozmowy.
     */
    @Test
    fun `toMime with a messageStore sets In-Reply-To and References to the last real email in the thread`() {
        val store = InMemoryMessageStore()
        val priorEmailReply = GatewayMessage(
            identity = Identity(
                messageId = MessageId("prior-reply"),
                correlationId = CorrelationId("correlation-1"),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference("<real-email-message-id@gmail.com>")
            ),
            source = Channel(type = ChannelType("email"), address = "correspondent@example.com"),
            destination = Channel(type = ChannelType("sms"), address = "+48123456789"),
            payload = Payload(content = "Odpowiedź uzytkownika")
        )
        store.insertIfAbsent(priorEmailReply.identity.externalReference, priorEmailReply)

        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val newSmsInSameThread = GatewayMessage(
            identity = Identity(
                messageId = MessageId("message-2"),
                correlationId = CorrelationId("correlation-1"),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference("sms-hash-2")
            ),
            source = Channel(type = ChannelType("sms"), address = "+48123456789"),
            destination = Channel(type = ChannelType("email"), address = "correspondent@example.com"),
            payload = Payload(content = "Kolejny SMS w tej samej rozmowie")
        )

        val mimeMessage = mapper.toMime(newSmsInSameThread, session, store)

        assertEquals("<real-email-message-id@gmail.com>", mimeMessage.getHeader("In-Reply-To", null))
        assertEquals("<real-email-message-id@gmail.com>", mimeMessage.getHeader("References", null))
    }

    @Test
    fun `toMime without a messageStore does not set threading headers - backward compatible`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("sms"), address = "+48123456789"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Tresc")
        )

        val mimeMessage = mapper.toMime(message, session)

        assertNull(mimeMessage.getHeader("In-Reply-To", null))
        assertNull(mimeMessage.getHeader("References", null))
    }

    @Test
    fun `toMime with a messageStore but no prior email in the thread does not set threading headers`() {
        val store = InMemoryMessageStore()
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val message = GatewayMessage(
            identity = createIdentity(),
            source = Channel(type = ChannelType("sms"), address = "+48123456789"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Pierwsza wiadomosc w watku")
        )

        val mimeMessage = mapper.toMime(message, session, store)

        assertNull(mimeMessage.getHeader("In-Reply-To", null))
        assertNull(mimeMessage.getHeader("References", null))
    }
}
