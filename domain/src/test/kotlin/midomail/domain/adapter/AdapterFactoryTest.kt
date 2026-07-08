package midomail.domain.adapter

import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.GatewayMessage
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryAttachmentStore
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.routing.RoutingEngine
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza mechanizm DI z SPEC-0010-Plugin-SDK-Contract.md, §Mechanizm DI: AdapterFactory jest
 * jedynym miejscem tworzenia instancji adaptera, wszystkie zależności przekazywane jako parametry.
 */
class AdapterFactoryTest {

    private class FakeConfiguration(val displayName: String) : AdapterConfiguration

    private class FakeAdapter(override val adapterId: AdapterId, private val displayName: String) : Adapter {
        override val adapterVersion: String = "1.0"
        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = setOf(Channel(type = ChannelType("email")))
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 0, 0, 0)
        override fun send(message: GatewayMessage) {}
    }

    private class FakeAdapterFactory : AdapterFactory {
        override fun create(configuration: AdapterConfiguration, ports: AdapterPorts): Adapter {
            configuration as FakeConfiguration
            return FakeAdapter(AdapterId("email-primary"), configuration.displayName)
        }
    }

    @Test
    fun `factory creates an adapter using only the parameters it receives`() {
        val factory = FakeAdapterFactory()

        val adapter = factory.create(FakeConfiguration(displayName = "Test"), createFakePorts())

        assertEquals(AdapterId("email-primary"), adapter.adapterId)
    }

    private fun createFakePorts(): AdapterPorts {
        val gatewayInbound = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(InMemoryMessageStore()),
            routingEngine = RoutingEngine(emptyList()),
            eventPublisher = InMemoryEventPublisher(),
            gatewayOutbound = object : GatewayOutbound {
                override fun send(message: GatewayMessage) {}
            }
        )
        return AdapterPorts(
            gatewayInbound = gatewayInbound,
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
}
