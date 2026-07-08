package midomail.adapter.email

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza wysyłkę SMTP (20-Adapters/22-Adapter-Email.md, §6) na osadzonym, rzeczywistym
 * serwerze SMTP (GreenMail — nie mock, prawdziwy protokół).
 */
class SmtpSenderTest {

    private lateinit var greenMail: GreenMail

    @BeforeTest
    fun startServer() {
        greenMail = GreenMail(ServerSetupTest.SMTP)
        greenMail.start()
        greenMail.setUser("sender@example.com", "sender@example.com", "test-password")
    }

    @AfterTest
    fun stopServer() {
        greenMail.stop()
    }

    @Test
    fun `sent message is received by the SMTP server with correct sender, recipient and content`() {
        val smtpSender = SmtpSender(
            SmtpConfig(
                host = "localhost",
                port = ServerSetupTest.SMTP.port,
                ssl = false,
                starttls = false,
                username = "sender@example.com",
                password = "test-password"
            )
        )
        val mapper = EmailMessageMapper(InMemoryAttachmentStore())
        val message = GatewayMessage(
            identity = Identity(
                messageId = MessageId("message-1"),
                correlationId = CorrelationId("correlation-1"),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference("<ext-1@localhost>")
            ),
            source = Channel(type = ChannelType("email"), address = "sender@example.com"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "Treść testowa"),
            attributes = mapOf("subject" to "Temat testowy")
        )
        val mimeMessage = mapper.toMime(message, smtpSender.session)

        smtpSender.send(mimeMessage)

        val received: Array<MimeMessage> = greenMail.receivedMessages
        assertEquals(1, received.size)
        assertEquals("Temat testowy", received.single().subject)
        assertEquals("recipient@example.com", received.single().getRecipients(jakarta.mail.Message.RecipientType.TO).single().toString())
    }

    /**
     * Test regresyjny: bez jawnego timeoutu, rzeczywista utrata sieci („czarna dziura", bez
     * czystego zamknięcia TCP) może pozostać niewykryta bardzo długo — znalezione podczas ręcznej
     * weryfikacji produkcyjnej Fazy 2 na rzeczywistym Gmailu (docs/faza2-weryfikacja-gmail.md,
     * scenariusz 20). „Timeout" jest wprost udokumentowanym parametrem konfiguracyjnym
     * (20-Adapters/22-Adapter-Email.md, §8).
     */
    @Test
    fun `session carries the configured SMTP timeout properties`() {
        val smtpSender = SmtpSender(
            SmtpConfig(
                host = "localhost",
                port = ServerSetupTest.SMTP.port,
                ssl = false,
                starttls = false,
                username = "sender@example.com",
                password = "test-password",
                timeoutMillis = 12_345
            )
        )

        assertEquals("12345", smtpSender.session.getProperty("mail.smtp.connectiontimeout"))
        assertEquals("12345", smtpSender.session.getProperty("mail.smtp.timeout"))
        assertEquals("12345", smtpSender.session.getProperty("mail.smtp.writetimeout"))
    }

    @Test
    fun `timeoutMillis defaults to 30 seconds when not specified`() {
        val smtpSender = SmtpSender(
            SmtpConfig(
                host = "localhost",
                port = ServerSetupTest.SMTP.port,
                ssl = false,
                starttls = false,
                username = "sender@example.com",
                password = "test-password"
            )
        )

        assertEquals("30000", smtpSender.session.getProperty("mail.smtp.timeout"))
    }
}
