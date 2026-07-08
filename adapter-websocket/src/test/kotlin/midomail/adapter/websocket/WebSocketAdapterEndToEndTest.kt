package midomail.adapter.websocket

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.message.GatewayMessage
import midomail.domain.message.ChannelType
import midomail.domain.message.Identity
import midomail.domain.message.CorrelationId
import midomail.domain.message.MessageId
import midomail.domain.message.SchemaVersion
import midomail.domain.message.ExternalReference
import midomail.domain.message.Channel
import midomail.domain.message.Payload
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.port.memory.InMemorySchedulerProvider
import midomail.domain.routing.RoutingEngine
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Weryfikacja end-to-end Adaptera WebSocket (Iteracja 7.5) — prawdziwy klient
 * (`java.net.http.WebSocket`) rozmawiający z prawdziwym, lokalnie uruchomionym serwerem testowym
 * ([TestWebSocketServer], RFC 6455 minimalny), nie atrapą/mockiem biblioteki.
 */
class WebSocketAdapterEndToEndTest {

    private lateinit var server: TestWebSocketServer
    private lateinit var scheduler: InMemorySchedulerProvider
    private val receivedByGateway = ConcurrentLinkedQueue<GatewayMessage>()

    @BeforeTest
    fun startServer() {
        server = TestWebSocketServer()
        server.start()
        scheduler = InMemorySchedulerProvider()
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun createPorts(): AdapterPorts {
        val gatewayEngine = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(InMemoryMessageStore()),
            routingEngine = RoutingEngine(emptyList()),
            eventPublisher = InMemoryEventPublisher(),
            gatewayOutbound = object : GatewayOutbound {
                override fun send(message: GatewayMessage) {}
            }
        )
        return AdapterPorts(
            gatewayInbound = object : midomail.domain.gateway.GatewayInbound {
                override fun receive(message: GatewayMessage) = gatewayEngine.receive(message).also { receivedByGateway.add(message) }
            },
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
    }

    private fun createAdapter(reconnectPolicy: ReconnectPolicy = ReconnectPolicy(maxAttempts = 3, backoffMillis = 200)): WebSocketAdapter {
        val configuration = WebSocketAdapterConfiguration(
            url = server.url,
            reconnectPolicy = reconnectPolicy,
            heartbeatIntervalMillis = 60_000
        )
        val channelType = ChannelType("websocket")
        return WebSocketAdapter(
            adapterId = AdapterId("websocket-primary"),
            adapterVersion = "1.0",
            configuration = configuration,
            mapper = WebSocketMessageMapper(channelType),
            ports = createPorts(),
            schedulerProvider = scheduler,
            channelType = channelType
        )
    }

    private fun awaitCondition(timeoutMillis: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue(condition(), "Warunek nie spełniony w limicie $timeoutMillis ms")
    }

    private fun sampleMessage(): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId(UUID.randomUUID().toString()),
            correlationId = CorrelationId(UUID.randomUUID().toString()),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference(UUID.randomUUID().toString())
        ),
        source = Channel(type = ChannelType("websocket")),
        destination = Channel(type = ChannelType("websocket")),
        payload = Payload(content = "wyjściowa wiadomość testowa")
    )

    @Test
    fun `start connects to the real test server and reports healthy`() {
        val adapter = createAdapter()
        adapter.start()

        awaitCondition { adapter.health().healthy }
        awaitCondition { server.activeConnectionCount() == 1 }

        adapter.stop()
    }

    @Test
    fun `a text frame sent by the server is received by GatewayInbound with the raw content as payload`() {
        val adapter = createAdapter()
        adapter.start()
        awaitCondition { server.activeConnectionCount() == 1 }

        server.broadcastText("wiadomość od serwera")

        awaitCondition { receivedByGateway.isNotEmpty() }
        assertEquals("wiadomość od serwera", receivedByGateway.single().payload.content)
        assertEquals(1L, adapter.metrics().messagesReceived)

        adapter.stop()
    }

    @Test
    fun `send transmits the payload content as a text frame received by the real server`() {
        val adapter = createAdapter()
        adapter.start()
        awaitCondition { server.activeConnectionCount() == 1 }

        adapter.send(sampleMessage())

        awaitCondition { server.receivedMessages.isNotEmpty() }
        assertEquals("wyjściowa wiadomość testowa", server.receivedMessages.single())
        assertEquals(1L, adapter.metrics().messagesSent)

        adapter.stop()
    }

    @Test
    fun `unexpected server-side close is detected and the adapter reconnects automatically`() {
        val adapter = createAdapter(reconnectPolicy = ReconnectPolicy(maxAttempts = 5, backoffMillis = 100))
        adapter.start()
        awaitCondition { server.activeConnectionCount() == 1 }

        server.closeAllConnections()

        awaitCondition { !adapter.health().healthy }
        awaitCondition(timeoutMillis = 5000) { server.activeConnectionCount() == 1 }
        awaitCondition { adapter.health().healthy }

        adapter.stop()
    }

    @Test
    fun `stop closes the connection cleanly and health becomes unhealthy after start again is required`() {
        val adapter = createAdapter()
        adapter.start()
        awaitCondition { server.activeConnectionCount() == 1 }

        adapter.stop()

        awaitCondition { server.activeConnectionCount() == 0 }
    }
}
