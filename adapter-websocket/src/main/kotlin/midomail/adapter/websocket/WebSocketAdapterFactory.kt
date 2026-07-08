package midomail.adapter.websocket

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterConfiguration
import midomail.domain.adapter.AdapterFactory
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.message.ChannelType
import midomail.domain.port.SchedulerProvider

/**
 * Jedyne miejsce tworzenia instancji [WebSocketAdapter] (SPEC-0010-Plugin-SDK-Contract.md,
 * §Mechanizm DI) — wszystkie zależności przekazywane jako parametry [create].
 */
class WebSocketAdapterFactory(
    private val adapterId: AdapterId,
    private val schedulerProvider: SchedulerProvider
) : AdapterFactory {

    override fun create(configuration: AdapterConfiguration, ports: AdapterPorts): Adapter {
        check(configuration is WebSocketAdapterConfiguration) {
            "WebSocketAdapterFactory wymaga WebSocketAdapterConfiguration, otrzymano: ${configuration::class}"
        }
        val channelType = ChannelType("websocket")
        return WebSocketAdapter(
            adapterId = adapterId,
            adapterVersion = "1.0",
            configuration = configuration,
            mapper = WebSocketMessageMapper(channelType),
            ports = ports,
            schedulerProvider = schedulerProvider,
            channelType = channelType
        )
    }
}
