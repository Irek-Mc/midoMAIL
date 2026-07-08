package midomail.adapter.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
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
import java.io.ByteArrayInputStream
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Potwierdza mapowanie MIME → GatewayMessage i wątkowanie (20-Adapters/22-Adapter-Email.md, §6):
 * ExternalReference = Message-ID (RFC 5322); wątkowanie przez In-Reply-To/References.
 */
class EmailMessageMapperFromMimeTest {

    private val session: Session = Session.getInstance(Properties())

    private fun createRawMime(
        messageId: String,
        from: String = "sender@example.com",
        to: String = "recipient@example.com",
        subject: String = "Temat",
        body: String = "Treść",
        inReplyTo: String? = null,
        references: String? = null
    ): MimeMessage {
        val raw = buildString {
            append("Message-ID: $messageId\r\n")
            append("From: $from\r\n")
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            inReplyTo?.let { append("In-Reply-To: $it\r\n") }
            references?.let { append("References: $it\r\n") }
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("\r\n")
            append(body)
        }
        // UTF-8 (nie US_ASCII) - polskie znaki diakrytyczne (np. domyślne "Treść" powyżej, albo
        // "napisał(a)" w testach cytowania odpowiedzi) w przeciwnym razie ulegałyby cichej
        // korupcji do "?" bez żadnej asercji to wcześniej wychwytującej.
        return MimeMessage(session, ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
    }

    private fun createStoredMessage(
        externalReference: String,
        correlationId: String,
        messageId: String,
        source: Channel = Channel(type = ChannelType("email"))
    ): GatewayMessage =
        GatewayMessage(
            identity = Identity(
                messageId = MessageId(messageId),
                correlationId = CorrelationId(correlationId),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference(externalReference)
            ),
            source = source,
            destination = Channel(type = ChannelType("email")),
            payload = Payload(content = "oryginał")
        )

    @Test
    fun `ExternalReference is the Message-ID header`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val mime = createRawMime(messageId = "<root@localhost>")

        val message = mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())

        assertEquals(ExternalReference("<root@localhost>"), message.identity.externalReference)
    }

    @Test
    fun `a message without a Message-ID header is rejected`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val raw = "From: sender@example.com\r\nTo: recipient@example.com\r\nSubject: brak id\r\n\r\ntresc"
        val mime = MimeMessage(session, ByteArrayInputStream(raw.toByteArray(Charsets.US_ASCII)))

        assertFailsWith<IllegalStateException> {
            mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())
        }
    }

    @Test
    fun `a message without a thread correlates itself as the root of a new thread`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val mime = createRawMime(messageId = "<root@localhost>")

        val message = mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())

        assertNull(message.identity.causationId)
    }

    @Test
    fun `a reply with In-Reply-To shares the CorrelationId of the original and points to it via CausationId`() {
        val store = InMemoryMessageStore()
        val original = createStoredMessage(externalReference = "<root@localhost>", correlationId = "thread-1", messageId = "message-root")
        store.insertIfAbsent(ExternalReference("<root@localhost>"), original)
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val reply = createRawMime(messageId = "<reply@localhost>", inReplyTo = "<root@localhost>")

        val message = mapper.fromMime(reply, ChannelType("email"), store)

        assertEquals(CorrelationId("thread-1"), message.identity.correlationId)
        assertEquals("message-root", message.identity.causationId?.value)
    }

    @Test
    fun `References is used as a fallback when In-Reply-To does not resolve`() {
        val store = InMemoryMessageStore()
        val original = createStoredMessage(externalReference = "<root@localhost>", correlationId = "thread-1", messageId = "message-root")
        store.insertIfAbsent(ExternalReference("<root@localhost>"), original)
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val reply = createRawMime(
            messageId = "<reply@localhost>",
            inReplyTo = "<not-found@localhost>",
            references = "<root@localhost> <not-found@localhost>"
        )

        val message = mapper.fromMime(reply, ChannelType("email"), store)

        assertEquals(CorrelationId("thread-1"), message.identity.correlationId)
    }

    /**
     * Iteracja 3.13 — pełny łańcuch SMS → Email → odpowiedź → SMS: gdy oryginał w MessageStore
     * pochodzi z INNEGO kanału niż e-mail (np. SMS przekazany dalej jako e-mail), odpowiedź musi
     * wrócić do TEGO kanału/adresu (numeru telefonu), nie do adresu e-mail nadawcy odpowiedzi.
     */
    @Test
    fun `a reply to a message that originated from a different channel routes the destination back to that channel`() {
        val store = InMemoryMessageStore()
        val originalSms = createStoredMessage(
            externalReference = "<root@localhost>",
            correlationId = "thread-1",
            messageId = "message-root",
            source = Channel(type = ChannelType("sms"), address = "+48500000000")
        )
        store.insertIfAbsent(ExternalReference("<root@localhost>"), originalSms)
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val reply = createRawMime(messageId = "<reply@localhost>", inReplyTo = "<root@localhost>")

        val message = mapper.fromMime(reply, ChannelType("email"), store)

        assertEquals(ChannelType("sms"), message.destination.type)
        assertEquals("+48500000000", message.destination.address)
    }

    @Test
    fun `a reply to a message that originated from the same channel keeps the To header as destination`() {
        val store = InMemoryMessageStore()
        val originalEmail = createStoredMessage(externalReference = "<root@localhost>", correlationId = "thread-1", messageId = "message-root")
        store.insertIfAbsent(ExternalReference("<root@localhost>"), originalEmail)
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val reply = createRawMime(messageId = "<reply@localhost>", to = "recipient@example.com", inReplyTo = "<root@localhost>")

        val message = mapper.fromMime(reply, ChannelType("email"), store)

        assertEquals(ChannelType("email"), message.destination.type)
        assertEquals("recipient@example.com", message.destination.address)
    }

    /**
     * Zgłoszenie nadzorcy projektu (zrzut ekranu Gmail Android): odpowiedź z dopisaną przez
     * klienta pocztowego cytowaną historią wraca w całości jako SMS zamiast wyłącznie nowej
     * treści — „nadawca ma nawet nie wiedzieć, że wiadomość nie pochodzi z telefonu".
     */
    @Test
    fun `a Gmail-style quoted reply (Polish napisal-a header) is stripped to only the new reply text`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val body = "odp\r\n\r\npon., 6 lip 2026 o 20:03\r\nmidoMAIL SMS/MMS <gateway@example.com> napisał(a):\r\n\r\n> Testing3\r\n>"
        val mime = createRawMime(messageId = "<reply@localhost>", body = body)

        val message = mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())

        assertEquals("odp", message.payload.content)
    }

    @Test
    fun `an English-style quoted reply (wrote header) is stripped to only the new reply text`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val body = "Thanks!\r\n\r\nOn Mon, Jul 6, 2026 at 8:03 PM midoMAIL SMS/MMS <gateway@example.com> wrote:\r\n\r\n> Testing3"
        val mime = createRawMime(messageId = "<reply@localhost>", body = body)

        val message = mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())

        assertEquals("Thanks!", message.payload.content)
    }

    @Test
    fun `a reply without any quoted history is left unchanged`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val mime = createRawMime(messageId = "<reply@localhost>", body = "Zwykla odpowiedz bez cytowania")

        val message = mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())

        assertEquals("Zwykla odpowiedz bez cytowania", message.payload.content)
    }

    @Test
    fun `content containing a greater-than sign but no quote header is left unchanged - avoids false positives`() {
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val mime = createRawMime(messageId = "<reply@localhost>", body = "wynik: 5 > 3, wygralismy")

        val message = mapper.fromMime(mime, ChannelType("email"), InMemoryMessageStore())

        assertEquals("wynik: 5 > 3, wygralismy", message.payload.content)
    }
}
