package midomail.adapter.email

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.adapter.Registry
import midomail.domain.event.SourceComponent
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.message.GatewayMessage
import midomail.domain.notification.EmailNotificationChannel
import midomail.domain.notification.NotificationResult
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.RoutingEngine
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Weryfikuje `EmailNotificationChannel` (`:domain`) na PRAWDZIWYM `EmailAdapter`/GreenMail — nie
 * atrapie (50-Quality/50-Testy.md, §5: „real embedded server over mocks"). `:domain` sam nie może
 * zależeć od `:adapter-email`, więc ten pełny przebieg żyje tutaj, nie w `:domain`.
 */
class EmailNotificationChannelIntegrationTest {

    private lateinit var greenMail: GreenMail

    @BeforeTest
    fun startServer() {
        greenMail = GreenMail(ServerSetupTest.SMTP_IMAP)
        greenMail.start()
        greenMail.setUser("ops@example.com", "ops@example.com", "test-password")
    }

    @AfterTest
    fun stopServer() {
        greenMail.stop()
    }

    @Test
    fun `an Alert delivered through EmailNotificationChannel arrives as a real email in GreenMail`() {
        val gatewayInbound = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(InMemoryMessageStore()),
            routingEngine = RoutingEngine(emptyList()),
            eventPublisher = InMemoryEventPublisher(),
            gatewayOutbound = object : GatewayOutbound {
                override fun send(message: GatewayMessage) {}
            }
        )
        val ports = AdapterPorts(
            gatewayInbound = gatewayInbound,
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
            eventPublisher = InMemoryEventPublisher()
        )
        val configuration = EmailAdapterConfiguration(
            smtp = SmtpConfig(host = "localhost", port = ServerSetupTest.SMTP.port, ssl = false, starttls = false, username = "ops@example.com", password = "test-password"),
            imap = ImapConfig(host = "localhost", port = ServerSetupTest.IMAP.port, imaps = false, starttls = false, username = "ops@example.com", password = "test-password", folder = "INBOX"),
            pollIntervalMillis = 50
        )
        val registry = Registry(InMemoryEventPublisher())
        val factory = EmailAdapterFactory(AdapterId("email-primary"), InMemorySchedulerProvider())
        val emailAdapter = factory.create(configuration, ports)
        registry.register(AdapterId("email-primary"), emailAdapter)

        val channel = EmailNotificationChannel(emailAdapter, fromAddress = "ops@example.com", toAddress = "ops@example.com")
        val alert = Alert(
            alertId = AlertId("alert-1"),
            level = AlertLevel.CRITICAL,
            source = SourceComponent("gsm-primary"),
            timestamp = Instant.parse("2026-07-06T12:00:00Z"),
            status = AlertStatus.ACTIVE,
            recommendedAction = "Sprawdź kartę SIM"
        )

        val result = channel.deliver(alert)

        assertEquals(NotificationResult.Delivered, result)
        val received = greenMail.receivedMessages
        assertEquals(1, received.size)
        assertTrue(received.single().subject.contains("CRITICAL"))
        assertTrue((received.single().content as String).contains("Sprawdź kartę SIM"))

        registry.stop(AdapterId("email-primary"))
    }
}
