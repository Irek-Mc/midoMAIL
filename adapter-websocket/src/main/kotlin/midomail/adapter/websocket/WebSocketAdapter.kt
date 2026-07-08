package midomail.adapter.websocket

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import midomail.domain.port.SchedulerProvider
import midomail.domain.port.TaskId
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Adapter WebSocket (20-Adapters/24-Adapter-WebSocket.md; SPEC-0026-WebSocket-Adapter-Contract.md).
 *
 * Klient (nie serwer, SPEC-0026 §1) oparty o `java.net.http.WebSocket` (JDK 11+, ADR-0035) —
 * zero zależności zewnętrznej. Łączy się wychodząco do [configuration.url][WebSocketAdapterConfiguration.url]
 * przy [start], ponawia połączenie wg [ReconnectPolicy] po nieoczekiwanym rozłączeniu/błędzie,
 * wysyła okresowe ramki ping jako heartbeat.
 */
class WebSocketAdapter(
    override val adapterId: AdapterId,
    override val adapterVersion: String,
    private val configuration: WebSocketAdapterConfiguration,
    private val mapper: WebSocketMessageMapper,
    private val ports: AdapterPorts,
    private val schedulerProvider: SchedulerProvider,
    private val channelType: ChannelType = ChannelType("websocket"),
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : Adapter {

    private val heartbeatTaskId = TaskId("websocket-adapter-${adapterId.value}-heartbeat")
    private val connectionLock = ReentrantLock()
    private var webSocket: WebSocket? = null
    private var stopped = false
    private val reconnectAttempts = AtomicInteger(0)

    private val healthy = AtomicBoolean(false)
    private var lastErrorDetails: String? = null
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    override fun start() {
        stopped = false
        connect()
        schedulerProvider.schedule(heartbeatTaskId, configuration.heartbeatIntervalMillis) { sendHeartbeat() }
    }

    override fun stop() {
        stopped = true
        schedulerProvider.cancel(heartbeatTaskId)
        connectionLock.withLock {
            webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "adapter stopped")
            webSocket = null
        }
    }

    override fun supportedChannels(): Set<Channel> = setOf(Channel(type = channelType))

    override fun supportedCapabilities(): Set<Capability> = emptySet()

    override fun health(): HealthStatus = HealthStatus(healthy = healthy.get(), details = lastErrorDetails)

    override fun metrics(): Metrics = Metrics(
        availableTokens = null,
        throttledCount = 0,
        cumulativeThrottlingEvents = 0,
        messagesSent = messagesSent.get(),
        messagesReceived = messagesReceived.get(),
        errorCount = errorCount.get()
    )

    override fun send(message: GatewayMessage) {
        val socket = connectionLock.withLock { webSocket }
        checkNotNull(socket) { "WebSocketAdapter (${adapterId.value}) nie ma aktywnego połączenia" }
        socket.sendText(mapper.toText(message), true)
        messagesSent.incrementAndGet()
    }

    private fun connect() {
        try {
            val socket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(configuration.url), AdapterListener())
                .join()
            connectionLock.withLock { webSocket = socket }
            healthy.set(true)
            lastErrorDetails = null
            reconnectAttempts.set(0)
        } catch (exception: Exception) {
            reportFailure(exception)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        val attempt = reconnectAttempts.incrementAndGet()
        if (attempt > configuration.reconnectPolicy.maxAttempts) {
            ports.logger.error("WebSocketAdapter (${adapterId.value}) wyczerpano próby ponownego połączenia ($attempt)")
            return
        }
        schedulerProvider.schedule(
            TaskId("websocket-adapter-${adapterId.value}-reconnect-$attempt"),
            configuration.reconnectPolicy.backoffMillis
        ) {
            schedulerProvider.cancel(TaskId("websocket-adapter-${adapterId.value}-reconnect-$attempt"))
            if (!stopped) connect()
        }
    }

    private fun sendHeartbeat() {
        val socket = connectionLock.withLock { webSocket } ?: return
        try {
            socket.sendPing(java.nio.ByteBuffer.allocate(0))
        } catch (exception: Exception) {
            reportFailure(exception)
        }
    }

    private fun reportFailure(exception: Exception) {
        errorCount.incrementAndGet()
        healthy.set(false)
        lastErrorDetails = exception.message
        ports.healthReporter.report(adapterId, HealthStatus(healthy = false, details = exception.message))
        ports.logger.error("WebSocketAdapter (${adapterId.value}) błąd", exception)
    }

    private inner class AdapterListener : WebSocket.Listener {
        private val textBuffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            textBuffer.append(data)
            if (last) {
                val message = mapper.fromText(textBuffer.toString())
                textBuffer.clear()
                ports.gatewayInbound.receive(message)
                messagesReceived.incrementAndGet()
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
            connectionLock.withLock { this@WebSocketAdapter.webSocket = null }
            healthy.set(false)
            lastErrorDetails = "Połączenie zamknięte: $statusCode $reason"
            if (!stopped) scheduleReconnect()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            connectionLock.withLock { this@WebSocketAdapter.webSocket = null }
            reportFailure(Exception(error))
            if (!stopped) scheduleReconnect()
        }
    }
}
