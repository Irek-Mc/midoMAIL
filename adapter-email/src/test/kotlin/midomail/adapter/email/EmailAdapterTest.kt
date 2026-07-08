package midomail.adapter.email

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.adapter.Registry
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.RoutingEngine
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Potwierdza spięcie EmailAdapter w Registry (Faza 1, Iteracja 8b) — rejestracja przez pełny,
 * rzeczywisty łańcuch (bez atrapy Adapter, w przeciwieństwie do RegistryTest z Fazy 1).
 */
class EmailAdapterTest {

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

    private fun createPorts(messageStore: InMemoryMessageStore = InMemoryMessageStore()): AdapterPorts {
        val gatewayInbound = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(InMemoryMessageStore()),
            routingEngine = RoutingEngine(emptyList()),
            eventPublisher = InMemoryEventPublisher(),
            gatewayOutbound = object : GatewayOutbound {
                override fun send(message: GatewayMessage) {}
            }
        )
        return AdapterPorts(
            gatewayInbound = gatewayInbound,
            messageStore = messageStore,
            logger = object : Logger {
                override fun info(message: String) {}
                override fun warn(message: String, throwable: Throwable?) {}
                override fun error(message: String, throwable: Throwable?) {}
            },
            healthReporter = object : HealthReporter {
                override fun report(adapterId: AdapterId, status: HealthStatus) {}
            },
            attachmentStore = InMemoryAttachmentStore(),
            eventPublisher = InMemoryEventPublisher()
        )
    }

    private fun createConfiguration(): EmailAdapterConfiguration = EmailAdapterConfiguration(
        smtp = SmtpConfig(
            host = "localhost",
            port = ServerSetupTest.SMTP.port,
            ssl = false,
            starttls = false,
            username = "recipient@example.com",
            password = "test-password"
        ),
        imap = ImapConfig(
            host = "localhost",
            port = ServerSetupTest.IMAP.port,
            imaps = false,
            starttls = false,
            username = "recipient@example.com",
            password = "test-password",
            folder = "INBOX"
        ),
        pollIntervalMillis = 50
    )

    @Test
    fun `registering the EmailAdapter in Registry reaches Ready and calls start`() {
        val registry = Registry(InMemoryEventPublisher())
        val schedulerProvider = InMemorySchedulerProvider()
        val factory = EmailAdapterFactory(AdapterId("email-primary"), schedulerProvider)
        val adapter = factory.create(createConfiguration(), createPorts())

        registry.register(AdapterId("email-primary"), adapter)

        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
        registry.stop(AdapterId("email-primary"))
    }

    @Test
    fun `supportedCapabilities declares SUPPORTS_ATTACHMENTS`() {
        val factory = EmailAdapterFactory(AdapterId("email-primary"), InMemorySchedulerProvider())
        val adapter = factory.create(createConfiguration(), createPorts())

        assertEquals(setOf(Capability.SUPPORTS_ATTACHMENTS), adapter.supportedCapabilities())
    }

    @Test
    fun `health is healthy immediately after start`() {
        val registry = Registry(InMemoryEventPublisher())
        val factory = EmailAdapterFactory(AdapterId("email-primary"), InMemorySchedulerProvider())
        val adapter = factory.create(createConfiguration(), createPorts())

        registry.register(AdapterId("email-primary"), adapter)

        assertEquals(HealthStatus(healthy = true), adapter.health())
        registry.stop(AdapterId("email-primary"))
    }

    /**
     * Test regresyjny znaleziony podczas ręcznej weryfikacji produkcyjnej Fazy 2 na rzeczywistym
     * Gmailu (docs/faza2-weryfikacja-gmail.md, scenariusz 20): po zerwaniu połączenia IMAP,
     * EmailAdapter nigdy samodzielnie nie odzyskiwał połączenia, nawet po przywróceniu sieci —
     * `pollOnce()` wiecznie ponawiał próby na tym samym, martwym obiekcie `Store`.
     */
    @Test
    fun `regresja - EmailAdapter reconnects automatically after IMAP connection loss`() {
        val configuration = createConfiguration().let {
            it.copy(imap = it.imap.copy(timeoutMillis = 2_000), pollIntervalMillis = 100)
        }
        val registry = Registry(InMemoryEventPublisher())
        val factory = EmailAdapterFactory(AdapterId("email-primary"), InMemorySchedulerProvider())
        val adapter = factory.create(configuration, createPorts())

        registry.register(AdapterId("email-primary"), adapter)
        assertTrue(adapter.health().healthy, "Adapter powinien być zdrowy zaraz po starcie")

        // Symulacja utraty połączenia: zatrzymanie serwera IMAP pod adapterem.
        greenMail.stop()
        val becameUnhealthy = waitUntil(timeoutMillis = 10_000) { !adapter.health().healthy }
        assertTrue(becameUnhealthy, "Adapter powinien wykryć utratę połączenia IMAP")

        // Symulacja przywrócenia sieci: nowy serwer na tym samym porcie testowym.
        greenMail = GreenMail(ServerSetupTest.SMTP_IMAP)
        greenMail.start()
        greenMail.setUser("recipient@example.com", "recipient@example.com", "test-password")

        val recovered = waitUntil(timeoutMillis = 10_000) { adapter.health().healthy }
        assertTrue(recovered, "Adapter powinien samodzielnie odzyskać połączenie po jego przywróceniu")

        registry.stop(AdapterId("email-primary"))
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    /**
     * Test regresyjny (ADR-0015-Rejestracja-Wyslanego-Message-Id.md): znaleziony podczas ręcznej
     * weryfikacji produkcyjnej Fazy 2 (scenariusz 3, obejście tylko w harnessu) i ponownie podczas
     * budowy Iteracji 3.13 (łańcuch międzykanałowy) — `EmailAdapter.send()` musi zarejestrować
     * wysłaną wiadomość pod jej rzeczywistym Message-ID, żeby przyszła odpowiedź (`In-Reply-To`)
     * mogła się do niej odwołać.
     */
    @Test
    fun `regresja - send registers the sent message in MessageStore under its real Message-ID`() {
        val messageStore = InMemoryMessageStore()
        val registry = Registry(InMemoryEventPublisher())
        val factory = EmailAdapterFactory(AdapterId("email-primary"), InMemorySchedulerProvider())
        val adapter = factory.create(createConfiguration(), createPorts(messageStore))
        registry.register(AdapterId("email-primary"), adapter)

        val original = GatewayMessage(
            identity = Identity(
                messageId = MessageId(UUID.randomUUID().toString()),
                correlationId = CorrelationId(UUID.randomUUID().toString()),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference(UUID.randomUUID().toString())
            ),
            source = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            destination = Channel(type = ChannelType("email"), address = "recipient@example.com"),
            payload = Payload(content = "treść testowa")
        )

        adapter.send(original)

        val receivedMessages = greenMail.receivedMessages
        val sentMessageId = receivedMessages.single().getHeader("Message-ID").single()

        val stored = messageStore.findByExternalReference(ExternalReference(sentMessageId))
        assertNotNull(stored, "Wysłana wiadomość powinna być zarejestrowana pod rzeczywistym Message-ID")
        assertEquals(original.identity.messageId, stored.identity.messageId)

        registry.stop(AdapterId("email-primary"))
    }
}
