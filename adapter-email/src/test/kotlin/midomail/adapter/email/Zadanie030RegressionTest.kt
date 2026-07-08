package midomail.adapter.email

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.adapter.Registry
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingConditions
import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test regresyjny odtwarzający dokładnie błąd zdiagnozowany w wersji 1.x (Zadanie 030):
 * ta sama odpowiedź e-mail, odebrana dwukrotnie przez IMAP (brak `\Seen` — 22-Adapter-Email.md,
 * §6), prowadziła do dwukrotnego dostarczenia w Gateway. Tu, przez pełny, rzeczywisty łańcuch
 * EmailAdapter → GatewayInbound.receive → ExactlyOnceEngine → GatewayEngine, prowadzi do
 * dokładnie jednego dostarczenia.
 *
 * **Kryterium wyjścia Fazy 2** (50-Quality/55-Roadmap.md, §4): potwierdzone tym testem.
 */
class Zadanie030RegressionTest {

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

    @Test
    fun `regresja Zadanie 030 - the same IMAP message polled repeatedly results in exactly one delivery`() {
        val eventPublisher = InMemoryEventPublisher()
        val sentMessages = CopyOnWriteArrayList<GatewayMessage>()
        val gatewayEngine = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(InMemoryMessageStore()),
            routingEngine = RoutingEngine(
                listOf(
                    RoutingRule(
                        ruleId = RuleId("email-loopback"),
                        priority = 100,
                        conditions = RoutingConditions(sourceChannel = ChannelType("email")),
                        targetChannel = ChannelType("email"),
                        targetAdapter = AdapterId("email-primary"),
                        deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                        version = RuleVersion("1")
                    )
                )
            ),
            eventPublisher = eventPublisher,
            gatewayOutbound = object : GatewayOutbound {
                override fun send(message: GatewayMessage) {
                    sentMessages.add(message)
                }
            }
        )
        val ports = AdapterPorts(
            gatewayInbound = gatewayEngine,
            messageStore = InMemoryMessageStore(),
            logger = object : Logger {
                override fun info(message: String) {}
                override fun warn(message: String, throwable: Throwable?) {}
                override fun error(message: String, throwable: Throwable?) {}
            },
            healthReporter = object : HealthReporter {
                override fun report(adapterId: AdapterId, status: HealthStatus) {}
            },
            attachmentStore = InMemoryAttachmentStore(),
            eventPublisher = eventPublisher
        )
        val registry = Registry(InMemoryEventPublisher())
        val schedulerProvider = InMemorySchedulerProvider()
        val factory = EmailAdapterFactory(AdapterId("email-primary"), schedulerProvider)
        val configuration = EmailAdapterConfiguration(
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
            // Krótki interwał, aby w czasie testu wystąpiło wiele cykli pollingu nad tą samą,
            // wciąż nieoznaczoną jako \Seen, wiadomością.
            pollIntervalMillis = 30
        )
        val adapter = factory.create(configuration, ports)

        // Odpowiedź e-mail dostarczona RAZ do skrzynki — dokładnie scenariusz Zadania 030.
        GreenMailUtil.sendTextEmail(
            "recipient@example.com",
            "sender@example.com",
            "Odpowiedź klienta",
            "Treść odpowiedzi",
            ServerSetupTest.SMTP
        )

        registry.register(AdapterId("email-primary"), adapter)
        try {
            // Wystarczająco długo, aby polling (co 30 ms) odczytał tę samą wiadomość
            // wielokrotnie — GreenMail nigdy nie ustawia \Seen bez jawnego potwierdzenia.
            Thread.sleep(250)
        } finally {
            registry.stop(AdapterId("email-primary"))
        }

        assertEquals(1, sentMessages.size, "Ta sama odpowiedź odebrana wielokrotnie przez IMAP musi zostać dostarczona dokładnie raz")
        assertEquals(1, eventPublisher.events().size, "Dokładnie jedno zdarzenie domenowe powinno zostać opublikowane")
    }
}
