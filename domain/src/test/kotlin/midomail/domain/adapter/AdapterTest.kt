package midomail.domain.adapter

import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemoryEventPublisher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza, że pełny interfejs Adapter (SPEC-0010-Plugin-SDK-Contract.md) jest zgodnym
 * rozszerzeniem AdapterLifecycle — Registry (Faza 1, Iteracja 8b) przyjmuje dowolny Adapter
 * bez żadnej zmiany własnego kontraktu.
 */
class AdapterTest {

    private class FakeAdapter(
        override val adapterId: AdapterId,
        override val adapterVersion: String = "1.0"
    ) : Adapter {
        var started = false
        var stopped = false
        var sentMessages = mutableListOf<GatewayMessage>()

        override fun start() {
            started = true
        }

        override fun stop() {
            stopped = true
        }

        override fun supportedChannels(): Set<Channel> = setOf(Channel(type = ChannelType("email")))
        override fun supportedCapabilities(): Set<Capability> = setOf(Capability.SUPPORTS_ATTACHMENTS)
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(
            availableTokens = null,
            throttledCount = 0,
            cumulativeThrottlingEvents = 0,
            messagesSent = 0,
            messagesReceived = 0,
            errorCount = 0
        )

        override fun send(message: GatewayMessage) {
            sentMessages.add(message)
        }
    }

    @Test
    fun `Registry accepts an Adapter wherever it expects an AdapterLifecycle`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapter = FakeAdapter(AdapterId("email-primary"))

        registry.register(AdapterId("email-primary"), adapter)

        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
        assert(adapter.started)
    }

    @Test
    fun `supportedCapabilities is checked generically via Set contains`() {
        val adapter = FakeAdapter(AdapterId("email-primary"))

        assertEquals(setOf(Capability.SUPPORTS_ATTACHMENTS), adapter.supportedCapabilities())
    }

    @Test
    fun `health and metrics expose the SPEC-0015 fields`() {
        val adapter = FakeAdapter(AdapterId("email-primary"))

        assertEquals(HealthStatus(healthy = true), adapter.health())
        assertEquals(0L, adapter.metrics().messagesSent)
    }
}
