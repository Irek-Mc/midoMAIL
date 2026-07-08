package midomail.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import midomail.adapter.email.EmailAdapterConfiguration
import midomail.adapter.email.EmailAdapterFactory
import midomail.adapter.email.ImapConfig
import midomail.adapter.email.SmtpConfig
import midomail.adapter.gsm.GsmAdapterConfiguration
import midomail.adapter.gsm.GsmAdapterFactory
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.adapter.Registry
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.diagnostics.EventStoreSystemLogger
import midomail.domain.error.GatewayError
import midomail.domain.error.toAlert
import midomail.domain.event.SourceComponent
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.event.Event
import midomail.domain.health.Alert
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertSink
import midomail.domain.health.HealthMonitor
import midomail.domain.message.ChannelType
import midomail.domain.notification.EmailNotificationChannel
import midomail.domain.notification.EscalationScheduler
import midomail.domain.notification.NotificationRouter
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryEventStore
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingConditions
import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import midomail.domain.statistics.StatisticsAggregator
import midomail.notification.webhook.WebhookNotificationChannel
import midomail.domain.notification.NotificationChannel as DomainNotificationChannel

/**
 * Foreground Service dla pracy 24/7 (40-Platforms/40-Android.md, §5 — Doze Mode, Foreground
 * Service) — od Iteracji 3.12 właściwe miejsce cyklu życia dla kompozycji Registry/GatewayEngine/
 * GsmAdapter (przeniesione z tymczasowego diagnostycznego podpięcia w `MainActivity`, Iteracja 3.8/3.9).
 *
 * `START_STICKY` — system ma odtworzyć serwis (z pustym Intentem) po zabiciu z powodu presji
 * pamięci, zgodnie z wymogiem pracy 24/7.
 *
 * Iteracja 3.13 — pełny łańcuch międzykanałowy: `EmailAdapter` dołącza do tej samej kompozycji.
 * Poświadczenia SMTP/IMAP odczytywane z [AndroidKeystoreSecretStore] (Iteracja 3.1) — nigdy nie są
 * zaszyte w kodzie. Jeśli nie zostały jeszcze wprowadzone (`DebugCredentialProvisioningReceiver`),
 * `EmailAdapter` nie jest rejestrowany — Gateway działa w trybie wyłącznie GSM, bez błędu.
 */
class GatewayForegroundService : Service() {

    private val outbound = AdapterRegistryOutbound()
    private lateinit var registry: Registry
    private lateinit var healthMonitor: HealthMonitor
    private lateinit var statisticsAggregator: StatisticsAggregator
    private lateinit var escalationScheduler: EscalationScheduler
    private lateinit var diagnosticsFacade: DiagnosticsFacade
    private lateinit var systemLogger: EventStoreSystemLogger

    /**
     * Migawka adapterów dla ekranu statusu (ADR-0038) — ustawiana raz, na końcu [startGateway]
     * (wątek tła), odczytywana z wątku UI przez [statusSnapshot] — stąd `@Volatile`, nie
     * synchronizacja: pojedynczy zapis, wielokrotny odczyt, spójność „ostatnia zbudowana lista"
     * wystarcza dla ekranu diagnostycznego odświeżanego co kilka sekund.
     */
    @Volatile private var registeredAdapters: List<Adapter> = emptyList()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // EmailAdapter.start() łączy się z IMAP synchronicznie (blokujące gniazdo) - Android
        // zabrania I/O sieciowego na wątku głównym (NetworkOnMainThreadException, znalezione na
        // urządzeniu, Iteracja 3.13). Service.onCreate() musi pozostać szybki/nieblokujący.
        Thread(::startGateway, "midomail-gateway-init").start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        if (::registry.isInitialized) {
            registry.stop(GSM_ADAPTER_ID)
            if (registry.stateOf(EMAIL_ADAPTER_ID) != null) {
                registry.stop(EMAIL_ADAPTER_ID)
            }
        }
        if (::healthMonitor.isInitialized) healthMonitor.stop()
        if (::statisticsAggregator.isInitialized) statisticsAggregator.stop()
        if (::escalationScheduler.isInitialized) escalationScheduler.stop()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Migawka stanu adapterów dla ekranu statusu (ADR-0038) — patrz [AdapterSnapshot]. */
    fun statusSnapshot(): List<AdapterSnapshot> = buildAdapterSnapshots(registeredAdapters)

    private fun startGateway() {
        val secretStore = AndroidKeystoreSecretStore(applicationContext)
        val emailCredentials = readEmailCredentials(secretStore)
        val webhookUrl = secretStore.read(SECRET_REF_WEBHOOK_URL)

        val eventStore = InMemoryEventStore()
        val eventPublisher = RecordingEventPublisher(InMemoryEventPublisher(), eventStore)
        val messageStore = InMemoryMessageStore()
        systemLogger = EventStoreSystemLogger(eventStore, SourceComponent("GatewayForegroundService"))

        val gatewayEngine = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(messageStore),
            routingEngine = RoutingEngine(routingRules(emailConfigured = emailCredentials != null)),
            eventPublisher = eventPublisher,
            gatewayOutbound = outbound
        )

        val ports = AdapterPorts(
            gatewayInbound = gatewayEngine,
            messageStore = messageStore,
            logger = AndroidLogger(),
            healthReporter = AndroidHealthReporter(),
            attachmentStore = InMemoryAttachmentStore(),
            eventPublisher = eventPublisher
        )

        registry = Registry(eventPublisher)
        diagnosticsFacade = DiagnosticsFacade(messageStore, eventStore, registry)

        // Utworzenie instancji adapterów PRZED rejestracją - `EmailNotificationChannel` potrzebuje
        // żywej instancji `EmailAdapter`, a `registerSafely()` (poniżej) potrzebuje gotowego
        // `alertSink`/routera - stąd kolejność: utworzenie -> kanały/router -> rejestracja.
        val gsmForwardAddress = emailCredentials?.forwardToAddress
        val gsmFactory = GsmAdapterFactory(GSM_ADAPTER_ID, applicationContext, AndroidMmsTransport(applicationContext))
        val gsmAdapter = gsmFactory.create(GsmAdapterConfiguration(forwardToAddress = gsmForwardAddress), ports)
        outbound.register(gsmAdapter)

        // Kapsułkuje adapter razem z poświadczeniami użytymi do jego utworzenia - unika `!!`
        // przy budowie EmailNotificationChannel poniżej (adapter i poświadczenia zawsze razem).
        data class EmailComponents(val adapter: Adapter, val credentials: EmailCredentials)

        val emailComponents = emailCredentials?.let { credentials ->
            val emailFactory = EmailAdapterFactory(EMAIL_ADAPTER_ID, InMemorySchedulerProvider(), fromDisplayName = "midoMAIL SMS/MMS")
            // Host/port konfigurowalne (ADR-0038) - domyślnie Gmail, jak dotychczas; nadpisywalne
            // przez ekran ustawień (MainActivity) dla innych dostawców e-mail.
            val smtp = resolveHostPort(secretStore, SECRET_REF_SMTP_HOST, SECRET_REF_SMTP_PORT, "smtp.gmail.com", 587)
            val imap = resolveHostPort(secretStore, SECRET_REF_IMAP_HOST, SECRET_REF_IMAP_PORT, "imap.gmail.com", 993)
            val emailConfiguration = EmailAdapterConfiguration(
                smtp = SmtpConfig(
                    host = smtp.host,
                    port = smtp.port,
                    ssl = false,
                    starttls = true,
                    username = credentials.username,
                    password = credentials.password
                ),
                imap = ImapConfig(
                    host = imap.host,
                    port = imap.port,
                    imaps = true,
                    starttls = false,
                    username = credentials.username,
                    password = credentials.password,
                    folder = "INBOX"
                ),
                pollIntervalMillis = 15_000
            )
            val adapter = emailFactory.create(emailConfiguration, ports).also { outbound.register(it) }
            EmailComponents(adapter, credentials)
        }
        val emailAdapter = emailComponents?.adapter
        if (emailAdapter == null) {
            Log.w(TAG, "Poświadczenia e-mail nie wprowadzone (Keystore) - EmailAdapter nie zarejestrowany, tryb wyłącznie GSM")
        }

        val emailNotificationChannel = emailComponents?.let {
            EmailNotificationChannel(it.adapter, fromAddress = it.credentials.username, toAddress = it.credentials.forwardToAddress)
        }
        val webhookNotificationChannel = webhookUrl?.let { WebhookNotificationChannel(it) }
        val notificationRouter = NotificationRouter(buildRoutingTable(emailNotificationChannel, webhookNotificationChannel))

        val schedulerProvider = InMemorySchedulerProvider()

        escalationScheduler = EscalationScheduler(
            schedulerProvider = schedulerProvider,
            checkIntervalMillis = ESCALATION_CHECK_INTERVAL_MILLIS,
            escalateAfterMillis = ::escalationThresholdFor,
            router = notificationRouter
        )
        escalationScheduler.start()

        val alertSink = AlertSink { alert ->
            notificationRouter.route(alert)
            escalationScheduler.register(alert)
        }

        val liveAdapters = mutableListOf<Adapter>()
        if (registerSafely(GSM_ADAPTER_ID, gsmAdapter, alertSink)) liveAdapters.add(gsmAdapter)
        if (emailAdapter != null && registerSafely(EMAIL_ADAPTER_ID, emailAdapter, alertSink)) liveAdapters.add(emailAdapter)
        registeredAdapters = liveAdapters

        healthMonitor = HealthMonitor(
            adapters = liveAdapters,
            schedulerProvider = schedulerProvider,
            checkIntervalMillis = HEALTH_CHECK_INTERVAL_MILLIS,
            alertSink = alertSink
        )
        healthMonitor.start()

        statisticsAggregator = StatisticsAggregator(
            adapters = liveAdapters,
            schedulerProvider = schedulerProvider,
            snapshotIntervalMillis = STATISTICS_SNAPSHOT_INTERVAL_MILLIS
        )
        statisticsAggregator.start()
    }

    /**
     * Routing Alert → kanały (38-Powiadomienia.md §4, wzorem przykładu z dokumentu) — kanały
     * pominięte, jeśli niedostępne (brak poświadczeń e-mail/URL webhooka), nie zgłasza błędu.
     * Kanał Push świadomie pominięty w tabeli — port istnieje (`PushNotificationChannel`), ale bez
     * skonfigurowanego prawdziwego mechanizmu nie ma dokąd kierować (Iteracja 4.0, decyzja zakresu).
     */
    private fun buildRoutingTable(
        email: DomainNotificationChannel?,
        webhook: DomainNotificationChannel?
    ): Map<AlertLevel, List<DomainNotificationChannel>> = buildMap {
        listOfNotNull(webhook, email).takeIf { it.isNotEmpty() }?.let { put(AlertLevel.CRITICAL, it) }
        listOfNotNull(email, webhook).takeIf { it.isNotEmpty() }?.let { put(AlertLevel.ERROR, it) }
        listOfNotNull(email).takeIf { it.isNotEmpty() }?.let { put(AlertLevel.WARNING, it) }
    }

    /** Progi eskalacji (38-Powiadomienia.md §5, przykładowe wartości z SPEC-0005). */
    private fun escalationThresholdFor(level: AlertLevel): Long? = when (level) {
        AlertLevel.CRITICAL -> 5 * 60_000L
        AlertLevel.ERROR -> 30 * 60_000L
        else -> null
    }

    /**
     * `Registry.register()` poprawnie rzuca wyjątek po nieudanym `adapter.start()`
     * (ADR-0014-Registry-Start-Failure.md, po przejściu do stanu `Failed`) — ale niezłapany
     * wyjątek stąd, w `Service.onCreate()`, crashowałby CAŁĄ aplikację (nie tylko jeden adapter),
     * łącznie z adapterami, które już poprawnie wystartowały. Znalezione na urządzeniu (Iteracja
     * 3.13): brak uprawnienia INTERNET powodował `SocketException` przy starcie `EmailAdapter`,
     * co bez tego zabezpieczenia zabijało cały proces `GatewayForegroundService`. Ten sam wzorzec
     * co poprawka harnessu Fazy 2 dla scenariuszy 19/20 (docs/faza2-weryfikacja-gmail.md).
     *
     * Iteracja 4.13: awaria jest TERAZ dodatkowo klasyfikowana jako `GatewayError.CriticalError`
     * (SPEC-0022, §Gdzie klasyfikacja zachodzi) i przekazana do [alertSink] — dokładnie punkt
     * integracji opisany w tej specyfikacji. Zwraca `true`, jeśli rejestracja się powiodła (żeby
     * wywołujący wiedział, czy dodać adapter do listy `HealthMonitor`/`StatisticsAggregator`).
     */
    private fun registerSafely(adapterId: AdapterId, adapter: midomail.domain.adapter.AdapterLifecycle, alertSink: AlertSink): Boolean {
        try {
            registry.register(adapterId, adapter)
            Log.i(TAG, "Registry: stan ${adapterId.value} po rejestracji = ${registry.stateOf(adapterId)}")
            return true
        } catch (exception: Exception) {
            Log.e(TAG, "Registry: rejestracja ${adapterId.value} nie powiodła się, stan = ${registry.stateOf(adapterId)}", exception)
            val error = GatewayError.CriticalError(
                message = "Rejestracja adaptera ${adapterId.value} nie powiodła się: ${exception.message}",
                cause = exception
            )
            alertSink.onAlert(error.toAlert(SourceComponent(adapterId.value)))
            return false
        }
    }

    /**
     * [forwardToAddress] jest ŚWIADOMIE odrębny od [username] — [username] to tożsamość SMTP/IMAP
     * samego Gateway (konto, z którego wysyła i na którym nasłuchuje odpowiedzi), [forwardToAddress]
     * to adres CZŁOWIEKA, do którego trafiają przekazane SMS/MMS. Pomylenie ich (użycie [username]
     * jako celu przekazania) było realnym błędem znalezionym na urządzeniu — przekazana wiadomość
     * trafiała do WŁASNEJ skrzynki Gateway zamiast do korespondenta.
     */
    private data class EmailCredentials(val username: String, val password: String, val forwardToAddress: String)

    private fun readEmailCredentials(secretStore: midomail.domain.port.SecretStore): EmailCredentials? {
        val username = secretStore.read(SECRET_REF_USERNAME) ?: return null
        val password = secretStore.read(SECRET_REF_PASSWORD) ?: return null
        val forwardToAddress = secretStore.read(SECRET_REF_FORWARD_TO) ?: return null
        return EmailCredentials(username, password, forwardToAddress)
    }

    /**
     * Reguły międzykanałowe (Iteracja 3.13): SMS/MMS → Email (przekazanie przychodzącej wiadomości
     * dalej jako e-mail), Email → GSM (odpowiedź wraca na SMS/MMS — `GsmAdapter.send()` sam wybiera
     * SMS czy MMS na podstawie obecności załączników, niezależnie od nominalnego `targetChannel`
     * tej reguły).
     *
     * Reguły email→gsm warunkowane TAKŻE po `destinationChannel` (`RoutingConditions`,
     * amendment tej samej iteracji) — nie tylko `sourceChannel=email`. Bez tego dopasowywałyby
     * KAŻDY e-mail w skrzynce, w tym niepowiązane wiadomości niebędące odpowiedzią na przekazany
     * SMS/MMS (`EmailMessageMapper.resolveDestination()` zostawia wtedy `destination.type=email`,
     * nie `sms`/`mms`) — znaleziony na urządzeniu realny błąd: próba wysłania takiego e-maila jako
     * SMS kończyła się `NULL_PDU` (adres e-mail jako `destination.address` zamiast numeru telefonu).
     *
     * Bez skonfigurowanego e-maila (`emailConfigured=false`) reguły SMS/MMS→Email nie miałyby dokąd
     * trafić (`EmailAdapter` niezarejestrowany) — pomijane, GSM działa wtedy w trybie loopback.
     */
    private fun routingRules(emailConfigured: Boolean): List<RoutingRule> {
        if (!emailConfigured) {
            return listOf(
                loopbackRule("sms-loopback", ChannelType("sms")),
                loopbackRule("mms-loopback", ChannelType("mms"))
            )
        }
        return listOf(
            RoutingRule(
                ruleId = RuleId("sms-to-email"),
                priority = 100,
                conditions = RoutingConditions(sourceChannel = ChannelType("sms")),
                targetChannel = ChannelType("email"),
                targetAdapter = EMAIL_ADAPTER_ID,
                deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                version = RuleVersion("1")
            ),
            RoutingRule(
                ruleId = RuleId("mms-to-email"),
                priority = 100,
                conditions = RoutingConditions(sourceChannel = ChannelType("mms")),
                targetChannel = ChannelType("email"),
                targetAdapter = EMAIL_ADAPTER_ID,
                deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                version = RuleVersion("1")
            ),
            RoutingRule(
                ruleId = RuleId("email-to-gsm-sms"),
                priority = 100,
                conditions = RoutingConditions(sourceChannel = ChannelType("email"), destinationChannel = ChannelType("sms")),
                targetChannel = ChannelType("sms"),
                targetAdapter = GSM_ADAPTER_ID,
                deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                version = RuleVersion("1")
            ),
            RoutingRule(
                ruleId = RuleId("email-to-gsm-mms"),
                priority = 100,
                conditions = RoutingConditions(sourceChannel = ChannelType("email"), destinationChannel = ChannelType("mms")),
                targetChannel = ChannelType("mms"),
                targetAdapter = GSM_ADAPTER_ID,
                deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                version = RuleVersion("1")
            )
        )
    }

    private fun loopbackRule(id: String, channelType: ChannelType): RoutingRule = RoutingRule(
        ruleId = RuleId(id),
        priority = 100,
        conditions = RoutingConditions(sourceChannel = channelType),
        targetChannel = channelType,
        targetAdapter = GSM_ADAPTER_ID,
        deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
        version = RuleVersion("1")
    )

    private class AndroidLogger : Logger {
        override fun info(message: String) {
            Log.i(TAG, message)
        }

        override fun warn(message: String, throwable: Throwable?) {
            Log.w(TAG, message, throwable)
        }

        override fun error(message: String, throwable: Throwable?) {
            Log.e(TAG, message, throwable)
        }
    }

    private class AndroidHealthReporter : HealthReporter {
        override fun report(adapterId: AdapterId, status: HealthStatus) {
            Log.i(TAG, "HealthReporter: adapter=${adapterId.value} healthy=${status.healthy} details=${status.details}")
        }
    }

    /**
     * `EventPublisher.publish()` (SPEC-0003, zamrożony) pozostaje niezmieniony — ten dekorator
     * przekazuje wywołanie do prawdziwej implementacji ORAZ dodatkowo zapisuje zdarzenie w
     * `EventStore` (SPEC-0023, §Decyzja: nowy port EventStore, EventPublisher pozostaje
     * niezmieniony: „Komponenty... wołają OBA porty"). `Registry`/`GatewayEngine` (zamrożone,
     * niezmodyfikowane) nie wiedzą o `EventStore` — wstrzyknięcie tego dekoratora zamiast
     * bezpośrednio `InMemoryEventPublisher` wystarcza, żeby ich istniejące wywołania
     * `eventPublisher.publish()` trafiały też do `EventStore`, bez dotykania ich kodu.
     */
    private class RecordingEventPublisher(
        private val inner: midomail.domain.port.EventPublisher,
        private val eventStore: midomail.domain.port.EventStore
    ) : midomail.domain.port.EventPublisher {
        override fun publish(event: Event) {
            inner.publish(event)
            eventStore.record(event)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "midoMAIL Gateway",
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("midoMAIL")
            .setContentText("Communication Gateway działa")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()

    companion object {
        /**
         * Referencja do żywej instancji serwisu (ADR-0038, §Mechanizm dostępu do stanu) —
         * `MainActivity` odczytuje [statusSnapshot] przez tę referencję (ten sam proces, brak
         * `android:process` w manifeście). `@Volatile`: ustawiana w `onCreate()`, zerowana w
         * `onDestroy()`, odczytywana z wątku UI.
         */
        @Volatile var instance: GatewayForegroundService? = null

        const val CHANNEL_ID = "midomail-gateway"
        const val NOTIFICATION_ID = 1
        const val TAG = "midoMAIL-gateway"
        val GSM_ADAPTER_ID = AdapterId("gsm-primary")
        val EMAIL_ADAPTER_ID = AdapterId("email-primary")
        const val SECRET_REF_USERNAME = "email-primary/username"
        const val SECRET_REF_PASSWORD = "email-primary/password"
        const val SECRET_REF_FORWARD_TO = "email-primary/forward-to"

        /** Host/port SMTP/IMAP konfigurowalne (ADR-0038) - domyślnie Gmail jeśli nieustawione,
         *  patrz [resolveHostPort]. */
        const val SECRET_REF_SMTP_HOST = "email-primary/smtp-host"
        const val SECRET_REF_SMTP_PORT = "email-primary/smtp-port"
        const val SECRET_REF_IMAP_HOST = "email-primary/imap-host"
        const val SECRET_REF_IMAP_PORT = "email-primary/imap-port"

        /** URL webhooka (np. Slack Incoming Webhook) - opcjonalny, wprowadzany tym samym mechanizmem
         *  co poświadczenia e-mail (`DebugCredentialProvisioningReceiver`, Iteracja 3.13, i od
         *  ADR-0038 też przez ekran ustawień). Brak wpisu = kanał Webhook pomijany w routingu,
         *  bez błędu. */
        const val SECRET_REF_WEBHOOK_URL = "notifications/webhook-url"

        /** Interwały Schedulera (Iteracja 4.13) - wartości produkcyjne; Iteracja 4.14 (weryfikacja
         *  ręczna) używa osobnej, krótszej konfiguracji do obserwacji na żywo w rozsądnym czasie. */
        const val HEALTH_CHECK_INTERVAL_MILLIS = 60_000L
        const val STATISTICS_SNAPSHOT_INTERVAL_MILLIS = 5 * 60_000L
        const val ESCALATION_CHECK_INTERVAL_MILLIS = 30_000L
    }
}
