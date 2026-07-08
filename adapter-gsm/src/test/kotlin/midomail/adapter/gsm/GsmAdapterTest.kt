package midomail.adapter.gsm

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import midomail.domain.gateway.GatewayInbound
import midomail.domain.gateway.ProcessingResult
import midomail.domain.message.Attachment
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GsmAdapterTest {

    private class FakeSmsTransport : SmsTransport {
        val sent = CopyOnWriteArrayList<Pair<String, String>>()
        var nextResult: SmsSendResult = SmsSendResult.Sent

        override fun send(
            destinationAddress: String,
            content: String,
            onSentResult: (SmsSendResult) -> Unit,
            onDeliveredResult: (SmsSendResult) -> Unit
        ) {
            sent.add(destinationAddress to content)
            onSentResult(nextResult)
        }
    }

    private class FakeMmsTransport : MmsTransport {
        val sentPdus = CopyOnWriteArrayList<ByteArray>()
        var nextResult: SmsSendResult = SmsSendResult.Sent

        override fun send(pdu: ByteArray, onResult: (SmsSendResult) -> Unit) {
            sentPdus.add(pdu)
            onResult(nextResult)
        }
    }

    private val attachmentStore = InMemoryAttachmentStore()
    private val eventPublisher = InMemoryEventPublisher()
    private val healthReports = CopyOnWriteArrayList<HealthStatus>()
    private val smsTransport = FakeSmsTransport()
    private val mmsTransport = FakeMmsTransport()

    private fun createPorts(gatewayInbound: GatewayInbound = AcceptingGatewayInbound()): AdapterPorts = AdapterPorts(
        gatewayInbound = gatewayInbound,
        messageStore = midomail.domain.port.memory.InMemoryMessageStore(),
        logger = object : Logger {
            override fun info(message: String) {}
            override fun warn(message: String, throwable: Throwable?) {}
            override fun error(message: String, throwable: Throwable?) {}
        },
        healthReporter = object : HealthReporter {
            override fun report(adapterId: AdapterId, status: HealthStatus) {
                healthReports.add(status)
            }
        },
        attachmentStore = attachmentStore,
        eventPublisher = eventPublisher
    )

    private class AcceptingGatewayInbound : GatewayInbound {
        override fun receive(message: GatewayMessage): ProcessingResult = ProcessingResult.Accepted(message)
    }

    private fun createAdapter(gatewayInbound: GatewayInbound = AcceptingGatewayInbound()): GsmAdapter = GsmAdapter(
        adapterId = AdapterId("gsm-primary"),
        adapterVersion = "1.0",
        smsTransport = smsTransport,
        mmsSender = MmsSender(mmsTransport),
        smsMapper = SmsMessageMapper(),
        mmsMapper = MmsMessageMapper(attachmentStore),
        truncator = SmsContentTruncator(maxSegments = 3, eventPublisher = eventPublisher),
        ports = createPorts(gatewayInbound)
    )

    private fun message(content: String, attachments: List<Attachment> = emptyList(), destination: String? = "+48123456789"): GatewayMessage =
        GatewayMessage(
            identity = Identity(
                messageId = MessageId(UUID.randomUUID().toString()),
                correlationId = CorrelationId(UUID.randomUUID().toString()),
                schemaVersion = SchemaVersion("2.0"),
                externalReference = ExternalReference(UUID.randomUUID().toString())
            ),
            source = Channel(type = ChannelType("sms"), address = "+48987654321"),
            destination = Channel(type = ChannelType("sms"), address = destination),
            payload = Payload(content = content, attachments = attachments)
        )

    @AfterTest
    fun cleanup() {
        GsmRuntime.mapper = null
        GsmRuntime.mmsMapper = null
        GsmRuntime.gatewayInbound = null
    }

    @Test
    fun `supportedCapabilities declares SUPPORTS_MMS, SUPPORTS_MULTIPART and SUPPORTS_ATTACHMENTS`() {
        val adapter = createAdapter()

        assertEquals(
            setOf(Capability.SUPPORTS_MMS, Capability.SUPPORTS_MULTIPART, Capability.SUPPORTS_ATTACHMENTS),
            adapter.supportedCapabilities()
        )
    }

    @Test
    fun `health is healthy after start and unhealthy after stop`() {
        val adapter = createAdapter()

        adapter.start()
        assertTrue(adapter.health().healthy)

        adapter.stop()
        assertEquals(false, adapter.health().healthy)
    }

    @Test
    fun `start wires GsmRuntime so incoming messages reach GatewayInbound`() {
        val adapter = createAdapter()
        adapter.start()

        assertTrue(GsmRuntime.mapper != null)
        assertTrue(GsmRuntime.mmsMapper != null)
        assertTrue(GsmRuntime.gatewayInbound != null)
    }

    @Test
    fun `a message without attachments is sent as SMS`() {
        val adapter = createAdapter()

        adapter.send(message(content = "hello"))

        val (destination, content) = smsTransport.sent.single()
        assertEquals("+48123456789", destination)
        assertEquals("hello", content)
        assertTrue(mmsTransport.sentPdus.isEmpty())
    }

    @Test
    fun `a message with attachments is sent as MMS`() {
        val adapter = createAdapter()
        val reference = attachmentStore.write(byteArrayOf(1, 2, 3))
        val attachment = Attachment(contentType = "image/jpeg", fileName = "photo.jpg", size = 3, dataReference = reference)

        adapter.send(message(content = "look at this", attachments = listOf(attachment)))

        assertEquals(1, mmsTransport.sentPdus.size)
        assertTrue(smsTransport.sent.isEmpty())
    }

    @Test
    fun `send throws when the destination address is missing`() {
        val adapter = createAdapter()

        assertFailsWith<IllegalArgumentException> {
            adapter.send(message(content = "hello", destination = null))
        }
    }

    @Test
    fun `a successful send increments messagesSent`() {
        val adapter = createAdapter()

        adapter.send(message(content = "hello"))

        assertEquals(1, adapter.metrics().messagesSent)
        assertEquals(0, adapter.metrics().errorCount)
    }

    @Test
    fun `a failed send increments errorCount and reports health`() {
        val adapter = createAdapter()
        adapter.start()
        smsTransport.nextResult = SmsSendResult.Failed("NO_SERVICE")

        adapter.send(message(content = "hello"))

        assertEquals(1, adapter.metrics().errorCount)
        assertTrue(healthReports.isNotEmpty())
        assertEquals(false, healthReports.last().healthy)
    }

    /**
     * Regresja (Iteracja 3.12): odbiór jest wywoływany z `BroadcastReceiver`
     * (`SmsDeliverReceiver`/`MmsWapPushReceiver`) — niezłapany wyjątek stamtąd byłby błędem
     * systemowym, nie tylko logicznym (np. downstream `RoutingEngine` trafia w adapter wymagający
     * `destination.address`, którego przychodzący SMS/MMS nie niesie — Iteracja 3.5/3.9).
     *
     * Przetwarzanie jest asynchroniczne od Iteracji 3.13 (patrz test poniżej) — `receive()` zwraca
     * się natychmiast z `Accepted`, rzeczywisty wynik (w tym błąd) trafia do `healthReporter`
     * dopiero po zakończeniu pracy w tle, stąd `waitUntil`.
     */
    @Test
    fun `an exception from downstream GatewayInbound is caught, not propagated, and reported as a health failure`() {
        val throwingGatewayInbound = object : GatewayInbound {
            override fun receive(message: GatewayMessage): ProcessingResult =
                throw IllegalArgumentException("brak destination.address w dalszym przetwarzaniu")
        }
        val adapter = createAdapter(throwingGatewayInbound)
        adapter.start()

        val result = GsmRuntime.gatewayInbound!!.receive(message(content = "przychodzący SMS"))
        assertTrue(result is ProcessingResult.Accepted)

        val processed = waitUntil(timeoutMillis = 2_000) { adapter.metrics().errorCount == 1L }
        assertTrue(processed, "Przetwarzanie w tle powinno zakończyć się w rozsądnym czasie")
        assertTrue(healthReports.isNotEmpty())
        assertEquals(false, healthReports.last().healthy)
    }

    /**
     * Odbiór jest wykonywany asynchronicznie (Iteracja 3.13) — `SmsDeliverReceiver`/`MmsWapPushReceiver`
     * działają na wątku głównym, a dalsze przetwarzanie (np. `EmailAdapter.send()` przy przekazaniu
     * SMS jako e-mail) wymaga blokującego I/O sieciowego, zabronionego na wątku głównym przez
     * Android (`NetworkOnMainThreadException`, znalezione na urządzeniu).
     */
    @Test
    fun `receive returns Accepted immediately and processes the message asynchronously`() {
        val processedMessages = java.util.concurrent.CopyOnWriteArrayList<GatewayMessage>()
        val slowGatewayInbound = object : GatewayInbound {
            override fun receive(message: GatewayMessage): ProcessingResult {
                Thread.sleep(50)
                processedMessages.add(message)
                return ProcessingResult.Accepted(message)
            }
        }
        val adapter = createAdapter(slowGatewayInbound)
        adapter.start()

        val result = GsmRuntime.gatewayInbound!!.receive(message(content = "test"))

        assertTrue(result is ProcessingResult.Accepted)
        assertTrue(processedMessages.isEmpty(), "Przetwarzanie nie powinno zakończyć się synchronicznie")

        val completed = waitUntil(timeoutMillis = 2_000) { processedMessages.isNotEmpty() }
        assertTrue(completed, "Przetwarzanie w tle powinno zakończyć się w rozsądnym czasie")
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}
