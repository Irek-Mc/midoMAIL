package midomail.adapter.email

import jakarta.mail.Store
import jakarta.mail.internet.MimeMessage
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Adapter Email (20-Adapters/22-Adapter-Email.md) — spina [SmtpSender] (wysyłka),
 * [ImapReceiver] (odbiór) i [EmailMessageMapper] (mapowanie) w jeden [Adapter].
 *
 * [start] łączy IMAP/SMTP i planuje cykliczny polling przez [SchedulerProvider]
 * (10-Core/16-Scheduler.md) — każdy cykl wywołuje [ImapReceiver.poll] i przekazuje każdą
 * odebraną wiadomość do [AdapterPorts.gatewayInbound] (SPEC-0010, §Porty przekazywane
 * adapterowi: „Rejestracja ExternalReference i weryfikacja Exactly Once zachodzi wewnątrz tej
 * operacji"). [poll] celowo zwraca tę samą wiadomość w kolejnych cyklach dopóki nie zniknie
 * z folderu (ImapReceiver, brak zależności od `\Seen`) — deduplikacja jest w całości
 * odpowiedzialnością Exactly Once, nie tej klasy.
 */
class EmailAdapter(
    override val adapterId: AdapterId,
    override val adapterVersion: String,
    private val smtpSender: SmtpSender,
    private val imapReceiver: ImapReceiver,
    private val mapper: EmailMessageMapper,
    private val ports: AdapterPorts,
    private val schedulerProvider: SchedulerProvider,
    private val pollIntervalMillis: Long
) : Adapter {

    private val pollTaskId = TaskId("email-adapter-${adapterId.value}-poll")
    private var imapStore: Store? = null
    // Nie "zdrowy", dopóki start() faktycznie się nie powiedzie (ADR-0014-Registry-Start-Failure.md)
    // — jeśli imapReceiver.connect() rzuci wyjątek przed healthy.set(true), pole nie może zostać
    // w mylącej wartości początkowej true.
    private val healthy = AtomicBoolean(false)
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    /**
     * Chroni [pollOnce] przed współbieżnym [stop] — bez tego `stop()` mógłby zamknąć folder IMAP,
     * podczas gdy zaplanowany cykl pollingu wciąż przetwarza już pobraną wiadomość
     * ([jakarta.mail.FolderClosedException], wykryte testem regresyjnym Zadania 030).
     */
    private val pollLock = ReentrantLock()

    override fun start() {
        imapStore = imapReceiver.connect()
        healthy.set(true)
        schedulerProvider.schedule(pollTaskId, pollIntervalMillis) { pollOnce() }
    }

    override fun stop() {
        schedulerProvider.cancel(pollTaskId)
        pollLock.withLock {
            imapStore?.let { imapReceiver.disconnect(it) }
            imapStore = null
        }
    }

    override fun supportedChannels(): Set<Channel> = setOf(Channel(type = ChannelType("email")))

    override fun supportedCapabilities(): Set<Capability> = setOf(Capability.SUPPORTS_ATTACHMENTS)

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
        try {
            val mimeMessage = mapper.toMime(message, smtpSender.session, ports.messageStore)
            smtpSender.send(mimeMessage)
            messagesSent.incrementAndGet()
            registerSentMessage(mimeMessage, message)
        } catch (exception: Exception) {
            reportFailure(exception)
            throw exception
        }
    }

    /**
     * Rejestruje wysłaną wiadomość pod jej rzeczywistym, przydzielonym przez serwer SMTP
     * nagłówkiem `Message-ID` (dostępny dopiero PO wysyłce) — jedyny, wąsko udokumentowany
     * wyjątek od zasady „adapter nigdy nie zapisuje bezpośrednio do Message Store"
     * (ADR-0015-Rejestracja-Wyslanego-Message-Id.md). Bez tego odpowiedź odbiorcy (`In-Reply-To`
     * wskazujący na ten Message-ID) nie mogłaby się skorelować z oryginałem w [EmailMessageMapper.fromMime].
     * Brak nagłówka (transport nie ujawnił identyfikatora) nie jest błędem — wątkowanie po prostu
     * nie zadziała dla tej wiadomości.
     */
    private fun registerSentMessage(mimeMessage: MimeMessage, message: GatewayMessage) {
        val messageId = mimeMessage.getHeader("Message-ID")?.firstOrNull() ?: return
        ports.messageStore.insertIfAbsent(ExternalReference(messageId), message)
    }

    /**
     * Jeśli poprzedni cykl zerwał połączenie, [imapStore] jest `null` (odrzucony przez
     * [discardBrokenConnection]) — ten cykl próbuje zbudować nowe połączenie od zera zamiast
     * wiecznie ponawiać próby na tym samym, martwym obiekcie [Store]. Samoleczy się w kolejnym
     * zaplanowanym cyklu pollingu, bez dodatkowej logiki retry/backoff (znalezione i naprawione
     * podczas ręcznej weryfikacji produkcyjnej Fazy 2 na rzeczywistym Gmailu,
     * docs/faza2-weryfikacja-gmail.md, scenariusz 20).
     */
    private fun pollOnce() {
        pollLock.withLock {
            val store = imapStore ?: reconnect() ?: return
            try {
                imapReceiver.poll(store).forEach { mimeMessage ->
                    val message = mapper.fromMime(mimeMessage, ChannelType("email"), ports.messageStore)
                    ports.gatewayInbound.receive(message)
                    messagesReceived.incrementAndGet()
                }
                healthy.set(true)
            } catch (exception: Exception) {
                reportFailure(exception)
                discardBrokenConnection()
            }
        }
    }

    private fun reconnect(): Store? = try {
        imapReceiver.connect().also { imapStore = it }
    } catch (exception: Exception) {
        reportFailure(exception)
        null
    }

    private fun discardBrokenConnection() {
        val store = imapStore ?: return
        imapStore = null
        try {
            imapReceiver.disconnect(store)
        } catch (exception: Exception) {
            // Połączenie już martwe - zamknięcie może samo rzucić; celowo ignorowane, imapStore
            // jest już wyzerowany, więc następny cykl i tak spróbuje zbudować je od nowa.
        }
    }

    private fun reportFailure(exception: Exception) {
        errorCount.incrementAndGet()
        healthy.set(false)
        val status = HealthStatus(healthy = false, details = exception.message)
        ports.healthReporter.report(adapterId, status)
        ports.logger.error("EmailAdapter (${adapterId.value}) błąd", exception)
    }
}
