package midomail.adapter.gsm

import midomail.domain.port.memory.InMemoryMessageStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SmsMessageMapperTest {

    private val mapper = SmsMessageMapper()

    @Test
    fun `source address is the sender and destination is unknown`() {
        val message = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)

        assertEquals("+48123456789", message.source.address)
        assertEquals("sms", message.source.type.value)
        assertNull(message.destination.address)
        assertEquals("sms", message.destination.type.value)
    }

    @Test
    fun `payload content is the message body`() {
        val message = mapper.fromSms(sender = "+48123456789", body = "Cześć, jak się masz?", timestampMillis = 1_000L)

        assertEquals("Cześć, jak się masz?", message.payload.content)
    }

    @Test
    fun `external reference is deterministic for identical input`() {
        val first = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)
        val second = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)

        assertEquals(first.identity.externalReference, second.identity.externalReference)
    }

    @Test
    fun `external reference differs when sender differs`() {
        val first = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)
        val second = mapper.fromSms(sender = "+48999999999", body = "test", timestampMillis = 1_000L)

        assert(first.identity.externalReference != second.identity.externalReference)
    }

    @Test
    fun `external reference differs when body differs`() {
        val first = mapper.fromSms(sender = "+48123456789", body = "test-a", timestampMillis = 1_000L)
        val second = mapper.fromSms(sender = "+48123456789", body = "test-b", timestampMillis = 1_000L)

        assert(first.identity.externalReference != second.identity.externalReference)
    }

    @Test
    fun `external reference differs when timestamp differs`() {
        val first = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)
        val second = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 2_000L)

        assert(first.identity.externalReference != second.identity.externalReference)
    }

    @Test
    fun `each mapped message is the root of its own new thread`() {
        val message = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)

        assertNull(message.identity.causationId)
    }

    @Test
    fun `a configured forwardToAddress becomes the destination address`() {
        val forwardingMapper = SmsMessageMapper(forwardToAddress = "gateway@example.com")

        val message = forwardingMapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)

        assertEquals("gateway@example.com", message.destination.address)
    }

    /**
     * ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md — zgłoszenie nadzorcy projektu: kolejny
     * SMS od tego samego numeru powinien kontynuować istniejącą rozmowę (jak natywna aplikacja
     * SMS), zamiast zawsze zaczynać nowy wątek.
     */
    @Test
    fun `with a messageStore, a second SMS from the same sender inherits the CorrelationId of the first`() {
        val store = InMemoryMessageStore()
        val mapper = SmsMessageMapper(messageStore = store)
        val first = mapper.fromSms(sender = "+48123456789", body = "pierwsza", timestampMillis = 1_000L)
        store.insertIfAbsent(first.identity.externalReference, first)

        val second = mapper.fromSms(sender = "+48123456789", body = "druga", timestampMillis = 2_000L)

        assertEquals(first.identity.correlationId, second.identity.correlationId)
        assertEquals(first.identity.messageId.value, second.identity.causationId?.value)
    }

    @Test
    fun `with a messageStore, an SMS from a different sender starts its own new thread`() {
        val store = InMemoryMessageStore()
        val mapper = SmsMessageMapper(messageStore = store)
        val first = mapper.fromSms(sender = "+48123456789", body = "pierwsza", timestampMillis = 1_000L)
        store.insertIfAbsent(first.identity.externalReference, first)

        val second = mapper.fromSms(sender = "+48999999999", body = "inny numer", timestampMillis = 2_000L)

        assertNotEquals(first.identity.correlationId, second.identity.correlationId)
        assertNull(second.identity.causationId)
    }

    @Test
    fun `with a messageStore, the very first SMS from a sender still starts a new thread`() {
        val store = InMemoryMessageStore()
        val mapper = SmsMessageMapper(messageStore = store)

        val message = mapper.fromSms(sender = "+48123456789", body = "test", timestampMillis = 1_000L)

        assertNull(message.identity.causationId)
    }

    @Test
    fun `without a messageStore, behavior is unchanged - each message is its own new thread`() {
        val mapper = SmsMessageMapper(messageStore = null)
        val first = mapper.fromSms(sender = "+48123456789", body = "pierwsza", timestampMillis = 1_000L)

        val second = mapper.fromSms(sender = "+48123456789", body = "druga", timestampMillis = 2_000L)

        assertNotEquals(first.identity.correlationId, second.identity.correlationId)
        assertNull(second.identity.causationId)
    }
}
