package midomail.adapter.email.manualverification

import midomail.adapter.email.EmailAdapterConfiguration
import midomail.adapter.email.EmailAdapterFactory
import midomail.adapter.email.EmailMessageMapper
import midomail.adapter.email.ImapConfig
import midomail.adapter.email.ImapReceiver
import midomail.adapter.email.SmtpConfig
import midomail.adapter.email.SmtpSender
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.Registry
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.gateway.ProcessingResult
import midomail.domain.message.Channel
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

/**
 * Spina rzeczywisty [EmailAdapter] (Faza 2, niezmieniony) z konfiguracją Gmail wyłącznie ze
 * zmiennych środowiskowych (docs/faza2-weryfikacja-gmail.md). Nie zmienia ani nie rozszerza
 * kodu produkcyjnego — wyłącznie composition root dla ręcznej weryfikacji, analogiczny do tego,
 * który powstanie w Fazie 3+ dla prawdziwej platformy.
 */
class GmailHarness(private val config: GmailTestConfig, pollIntervalMillis: Long = 5_000) {

    val messageStore = InMemoryMessageStore()
    val attachmentStore = InMemoryAttachmentStore()
    val eventPublisher = InMemoryEventPublisher()
    val sentToTransport = CopyOnWriteArrayList<GatewayMessage>()
    val schedulerProvider = InMemorySchedulerProvider()
    val registry = Registry(InMemoryEventPublisher())
    val adapterId = AdapterId("email-gmail-verification")

    private val gatewayEngine = GatewayEngine(
        exactlyOnceEngine = ExactlyOnceEngine(messageStore),
        routingEngine = RoutingEngine(
            listOf(
                RoutingRule(
                    ruleId = RuleId("email-loopback"),
                    priority = 100,
                    conditions = RoutingConditions(sourceChannel = ChannelType("email")),
                    targetChannel = ChannelType("email"),
                    targetAdapter = adapterId,
                    deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                    version = RuleVersion("1")
                )
            )
        ),
        eventPublisher = eventPublisher,
        gatewayOutbound = object : GatewayOutbound {
            override fun send(message: GatewayMessage) {
                sentToTransport.add(message)
            }
        }
    )

    private val ports = AdapterPorts(
        gatewayInbound = gatewayEngine,
        messageStore = messageStore,
        logger = fileLogger,
        healthReporter = loggingHealthReporter,
        attachmentStore = attachmentStore,
        eventPublisher = eventPublisher
    )

    private val configuration = EmailAdapterConfiguration(
        smtp = SmtpConfig(
            host = "smtp.gmail.com",
            port = 587,
            ssl = false,
            starttls = true,
            username = config.address,
            password = config.appPassword,
            timeoutMillis = 10_000
        ),
        imap = ImapConfig(
            host = "imap.gmail.com",
            port = 993,
            imaps = true,
            starttls = false,
            timeoutMillis = 10_000,
            username = config.address,
            password = config.appPassword,
            folder = "INBOX"
        ),
        pollIntervalMillis = pollIntervalMillis
    )

    val adapter: Adapter = EmailAdapterFactory(adapterId, schedulerProvider).create(configuration, ports)

    /** Bezpośredni dostęp do mappera/sendera/receivera — do scenariuszy z drobniejszą kontrolą. */
    val mapper = EmailMessageMapper(attachmentStore)
    val smtpSender = SmtpSender(configuration.smtp)
    val imapReceiver = ImapReceiver(configuration.imap)

    fun start() {
        registry.register(adapterId, adapter)
        VerificationLog.info("Adapter zarejestrowany, stan: ${registry.stateOf(adapterId)}")
    }

    fun stop() {
        registry.stop(adapterId)
        VerificationLog.info("Adapter zatrzymany")
    }

    fun receiveDirect(message: GatewayMessage): ProcessingResult = gatewayEngine.receive(message)
}
