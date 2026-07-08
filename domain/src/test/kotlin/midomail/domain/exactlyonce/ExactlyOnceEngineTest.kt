package midomail.domain.exactlyonce

import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.memory.InMemoryMessageStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt Exactly Once z 10-Core/17-Exactly-Once.md i SPEC-0008-Exactly-Once-Contract.md.
 *
 * Test regresyjny odtwarzający dokładnie błąd zdiagnozowany w wersji 1.x (Zadanie 030):
 * ta sama odpowiedź IMAP (ten sam ExternalReference), przetworzona dwukrotnie, musi zostać
 * zaakceptowana dokładnie raz.
 */
class ExactlyOnceEngineTest {

    private fun createMessage(messageId: String, externalReference: String): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId(messageId),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference(externalReference)
        ),
        source = Channel(type = ChannelType("email")),
        destination = Channel(type = ChannelType("gsm")),
        payload = Payload(content = "tak tak")
    )

    @Test
    fun `a message with a new ExternalReference is accepted`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())
        val externalReference = ExternalReference("<CAEEBHwNy@mail.gmail.com>")

        val result = engine.processIfNew(externalReference, createMessage("message-1", externalReference.value))

        assertIs<ExactlyOnceResult.Accepted>(result)
    }

    @Test
    fun `regresja Zadanie 030 - reprocessing the same ExternalReference is rejected as a duplicate`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())
        val externalReference = ExternalReference("<CAEEBHwNy@mail.gmail.com>")
        val firstAttempt = createMessage("message-1", externalReference.value)
        val secondAttempt = createMessage("message-2", externalReference.value)

        val firstResult = engine.processIfNew(externalReference, firstAttempt)
        val secondResult = engine.processIfNew(externalReference, secondAttempt)

        assertEquals(ExactlyOnceResult.Accepted(firstAttempt), firstResult)
        assertEquals(ExactlyOnceResult.Duplicate, secondResult)
    }

    @Test
    fun `different ExternalReference values are both accepted independently`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())

        val first = engine.processIfNew(
            ExternalReference("<first@localhost>"),
            createMessage("message-1", "<first@localhost>")
        )
        val second = engine.processIfNew(
            ExternalReference("<second@localhost>"),
            createMessage("message-2", "<second@localhost>")
        )

        assertIs<ExactlyOnceResult.Accepted>(first)
        assertIs<ExactlyOnceResult.Accepted>(second)
    }

    @Test
    fun `concurrent processing of the same ExternalReference accepts exactly once - atomicity under race`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())
        val externalReference = ExternalReference("<race@localhost>")
        val threadCount = 20
        val acceptedCount = AtomicInteger(0)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { index ->
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                val result = engine.processIfNew(externalReference, createMessage("message-$index", externalReference.value))
                if (result is ExactlyOnceResult.Accepted) {
                    acceptedCount.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, acceptedCount.get(), "Dokładnie jedno wywołanie powinno zostać zaakceptowane mimo współbieżności")
        assertEquals(1, engine.counters().processed)
        assertEquals(19, engine.counters().duplicatesPrevented)
    }

    // --- counters() (ADR-0034) ---

    @Test
    fun `counters start at zero`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())

        assertEquals(ExactlyOnceCounters(0, 0), engine.counters())
    }

    @Test
    fun `counters increments processed for each newly accepted ExternalReference`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())

        engine.processIfNew(ExternalReference("<first@localhost>"), createMessage("message-1", "<first@localhost>"))
        engine.processIfNew(ExternalReference("<second@localhost>"), createMessage("message-2", "<second@localhost>"))

        assertEquals(2, engine.counters().processed)
        assertEquals(0, engine.counters().duplicatesPrevented)
    }

    @Test
    fun `counters increments duplicatesPrevented for a repeated ExternalReference`() {
        val engine = ExactlyOnceEngine(InMemoryMessageStore())
        val externalReference = ExternalReference("<CAEEBHwNy@mail.gmail.com>")

        engine.processIfNew(externalReference, createMessage("message-1", externalReference.value))
        engine.processIfNew(externalReference, createMessage("message-2", externalReference.value))

        assertEquals(1, engine.counters().processed)
        assertEquals(1, engine.counters().duplicatesPrevented)
    }
}
