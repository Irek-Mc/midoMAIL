package midomail.adapter.gsm

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.gateway.GatewayInbound
import midomail.domain.gateway.ProcessingResult
import midomail.domain.message.Attachment
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Adapter GSM (20-Adapters/21-Adapter-GSM.md) — spina [SmsSender]/[MmsSender] (wysyłka),
 * odbiór push przez [GsmRuntime] ([SmsDeliverReceiver]/[MmsWapPushReceiver] w `:platform-android`,
 * instancjonowane przez system — brak pollingu, w przeciwieństwie do `EmailAdapter`, §9: „SMS/MMS
 * przychodzące są dostarczane push przez platformę"), [SmsMessageMapper]/[MmsMessageMapper]
 * (mapowanie) w jeden [Adapter].
 *
 * [send] wybiera transport na podstawie obecności załączników — SMS dla czystego tekstu
 * (z obcięciem przez [SmsContentTruncator]), MMS gdy `payload.attachments` niepuste. Wysyłka jest
 * asynchroniczna (wynik z `PendingIntent` przychodzi później) — `send()` zwraca się po samym
 * zgłoszeniu żądania do radia, zgodnie z 40-Platforms/40-Android.md, §5 („brak wyjątku nie oznacza
 * sukcesu") — rzeczywisty wynik trafia do [AdapterPorts.healthReporter]/[AdapterPorts.logger], nie
 * przez wyjątek z tej metody.
 *
 * **Świadomie poza zakresem tej iteracji:** wykrywanie braku zasięgu i zgłaszanie stanu `Degraded`
 * (21-Adapter-GSM.md, §9) wymagałoby `TelephonyManager`/nasłuchu stanu sieci — nieprzewidziane w
 * bieżącym zakresie uprawnień/manifestu; `health()` odzwierciedla wyłącznie wynik ostatnich
 * operacji wysyłki/odbioru, nie stan radia.
 */
class GsmAdapter(
    override val adapterId: AdapterId,
    override val adapterVersion: String,
    private val smsTransport: SmsTransport,
    private val mmsSender: MmsSender,
    private val smsMapper: SmsMessageMapper,
    private val mmsMapper: MmsMessageMapper,
    private val truncator: SmsContentTruncator,
    private val ports: AdapterPorts
) : Adapter {

    private val healthy = AtomicBoolean(false)
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    /**
     * `SmsDeliverReceiver`/`MmsWapPushReceiver` wywołują odbiór na wątku głównym (standardowy
     * cykl życia `BroadcastReceiver`) — dalsze przetwarzanie (`GatewayEngine` → routing → np.
     * `EmailAdapter.send()` przy przekazaniu SMS jako e-mail, Iteracja 3.13) wymaga blokującego
     * I/O sieciowego, co Android zabrania na wątku głównym (`NetworkOnMainThreadException`,
     * znalezione na urządzeniu). Proces `:platform-android` pozostaje żywy dzięki
     * `GatewayForegroundService` (Foreground Service), więc praca w tle przeżywa powrót
     * `onReceive()` bez potrzeby `goAsync()`.
     */
    private val receiveExecutor = Executors.newSingleThreadExecutor()

    override fun start() {
        GsmRuntime.mapper = smsMapper
        GsmRuntime.mmsMapper = mmsMapper
        GsmRuntime.gatewayInbound = object : GatewayInbound {
            override fun receive(message: GatewayMessage): ProcessingResult {
                receiveExecutor.execute { receiveOffMainThread(message) }
                return ProcessingResult.Accepted(message)
            }
        }
        healthy.set(true)
    }

    /**
     * Odbiór jest wywoływany z `BroadcastReceiver` (`SmsDeliverReceiver`/`MmsWapPushReceiver`) —
     * niezłapany wyjątek stamtąd byłby błędem systemowym (ANR/crash odbiornika), nie tylko błędem
     * logicznym. Awaria dalszego przetwarzania (np. `RoutingEngine` trafia w adapter wymagający
     * `destination.address`, którego przychodzący SMS/MMS nie niesie) jest zgłaszana przez
     * `healthReporter`/`logger`, nie przez wyjątek — a skoro wykonanie jest już asynchroniczne
     * (patrz `receiveExecutor` powyżej), i tak nie ma do kogo go rzucić.
     */
    private fun receiveOffMainThread(message: GatewayMessage) {
        try {
            ports.gatewayInbound.receive(message)
            messagesReceived.incrementAndGet()
        } catch (exception: Exception) {
            reportFailure(exception)
        }
    }

    override fun stop() {
        GsmRuntime.mapper = null
        GsmRuntime.mmsMapper = null
        GsmRuntime.gatewayInbound = null
        receiveExecutor.shutdown()
        healthy.set(false)
    }

    override fun supportedChannels(): Set<Channel> =
        setOf(Channel(type = ChannelType("sms")), Channel(type = ChannelType("mms")))

    override fun supportedCapabilities(): Set<Capability> =
        setOf(Capability.SUPPORTS_MMS, Capability.SUPPORTS_MULTIPART, Capability.SUPPORTS_ATTACHMENTS)

    override fun health(): HealthStatus = HealthStatus(healthy = healthy.get())

    override fun metrics(): Metrics = Metrics(
        availableTokens = null,
        throttledCount = 0,
        cumulativeThrottlingEvents = 0,
        messagesSent = messagesSent.get(),
        messagesReceived = messagesReceived.get(),
        errorCount = errorCount.get()
    )

    override fun send(message: GatewayMessage) {
        val destination = requireNotNull(message.destination.address) {
            "Channel.address docelowy jest wymagany do wysyłki SMS/MMS"
        }
        if (message.payload.attachments.isEmpty()) {
            sendAsSms(destination, message)
        } else {
            sendAsMms(destination, message)
        }
    }

    private fun sendAsSms(destination: String, message: GatewayMessage) {
        val content = truncator.truncateIfNeeded(message.payload.content, message.identity.correlationId)
        smsTransport.send(destination, content, onSentResult = ::handleSendResult)
    }

    private fun sendAsMms(destination: String, message: GatewayMessage) {
        val parts = buildList {
            if (message.payload.content.isNotEmpty()) {
                add(MmsPart(contentType = "text/plain", fileName = null, bytes = message.payload.content.toByteArray()))
            }
            message.payload.attachments.forEach { attachment -> add(toMmsPart(attachment)) }
        }
        mmsSender.send(destination, parts, onResult = ::handleSendResult)
    }

    private fun toMmsPart(attachment: Attachment): MmsPart = MmsPart(
        contentType = attachment.contentType,
        fileName = attachment.fileName,
        bytes = ports.attachmentStore.read(attachment.dataReference)
    )

    private fun handleSendResult(result: SmsSendResult) {
        when (result) {
            is SmsSendResult.Sent -> messagesSent.incrementAndGet()
            is SmsSendResult.Failed -> reportFailure(IllegalStateException("Wysyłka nie powiodła się: ${result.reason}"))
        }
    }

    private fun reportFailure(exception: Exception) {
        errorCount.incrementAndGet()
        healthy.set(false)
        val status = HealthStatus(healthy = false, details = exception.message)
        ports.healthReporter.report(adapterId, status)
        ports.logger.error("GsmAdapter (${adapterId.value}) błąd", exception)
    }
}
