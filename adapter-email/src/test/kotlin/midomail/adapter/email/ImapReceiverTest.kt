package midomail.adapter.email

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza odbiór IMAP (20-Adapters/22-Adapter-Email.md, §6) na rzeczywistym, osadzonym
 * serwerze GreenMail — w szczególności brak zależności od flagi `\Seen`.
 */
class ImapReceiverTest {

    private lateinit var greenMail: GreenMail

    @BeforeTest
    fun startServer() {
        greenMail = GreenMail(ServerSetupTest.SMTP_IMAP)
        greenMail.start()
        greenMail.setUser("recipient@example.com", "recipient@example.com", "test-password")
    }

    @AfterTest
    fun stopServer() {
        greenMail.stop()
    }

    private fun createReceiver(): ImapReceiver = ImapReceiver(
        ImapConfig(
            host = "localhost",
            port = ServerSetupTest.IMAP.port,
            imaps = false,
            starttls = false,
            username = "recipient@example.com",
            password = "test-password",
            folder = "INBOX"
        )
    )

    @Test
    fun `poll returns a message delivered to the mailbox`() {
        GreenMailUtil.sendTextEmail(
            "recipient@example.com",
            "sender@example.com",
            "Temat testowy",
            "Treść testowa",
            ServerSetupTest.SMTP
        )
        val receiver = createReceiver()
        val store = receiver.connect()

        val messages = receiver.poll(store)

        assertEquals(1, messages.size)
        assertEquals("Temat testowy", messages.single().subject)
        receiver.disconnect(store)
    }

    @Test
    fun `polling twice without any Seen flag change returns the same message both times - no dependency on Seen`() {
        GreenMailUtil.sendTextEmail(
            "recipient@example.com",
            "sender@example.com",
            "Temat testowy",
            "Treść testowa",
            ServerSetupTest.SMTP
        )
        val receiver = createReceiver()
        val store = receiver.connect()

        val firstPoll = receiver.poll(store)
        val secondPoll = receiver.poll(store)

        assertEquals(1, firstPoll.size)
        assertEquals(1, secondPoll.size)
        assertEquals(firstPoll.single().getHeader("Message-ID")?.firstOrNull(), secondPoll.single().getHeader("Message-ID")?.firstOrNull())
        receiver.disconnect(store)
    }

    /**
     * Test regresyjny: bez jawnego timeoutu, rzeczywista utrata sieci może pozostać niewykryta
     * bardzo długo — znalezione podczas ręcznej weryfikacji produkcyjnej Fazy 2 na rzeczywistym
     * Gmailu (docs/faza2-weryfikacja-gmail.md, scenariusz 20). „Timeout" jest wprost
     * udokumentowanym parametrem konfiguracyjnym (20-Adapters/22-Adapter-Email.md, §8).
     */
    @Test
    fun `session carries the configured IMAP timeout properties`() {
        val receiver = ImapReceiver(
            ImapConfig(
                host = "localhost",
                port = ServerSetupTest.IMAP.port,
                imaps = false,
                starttls = false,
                username = "recipient@example.com",
                password = "test-password",
                folder = "INBOX",
                timeoutMillis = 12_345
            )
        )

        assertEquals("12345", receiver.session.getProperty("mail.imap.connectiontimeout"))
        assertEquals("12345", receiver.session.getProperty("mail.imap.timeout"))
        assertEquals("12345", receiver.session.getProperty("mail.imap.writetimeout"))
    }

    @Test
    fun `timeoutMillis defaults to 30 seconds when not specified`() {
        val receiver = createReceiver()

        assertEquals("30000", receiver.session.getProperty("mail.imap.timeout"))
    }
}
