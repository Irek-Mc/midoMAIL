package midomail.platform.android

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.message.GatewayMessage
import midomail.domain.port.GatewayOutbound
import java.util.concurrent.ConcurrentHashMap

/**
 * `GatewayOutbound` wielo-adapterowy — `RoutingEngine` (10-Core/13-Routing.md) wyznacza
 * `destination.adapterId` na podstawie reguł routingu, ale sam `Registry` (10-Core/14-Registry-Adapterow.md)
 * celowo nie udostępnia wyszukiwania instancji adaptera po ID (śledzi wyłącznie stan cyklu życia) —
 * to jest odrębny, mały komponent kompozycyjny łączący `destination.adapterId` z konkretną
 * zarejestrowaną instancją [Adapter], żeby faktycznie wywołać jej `send()`.
 */
class AdapterRegistryOutbound : GatewayOutbound {

    private val adapters = ConcurrentHashMap<AdapterId, Adapter>()

    fun register(adapter: Adapter) {
        adapters[adapter.adapterId] = adapter
    }

    override fun send(message: GatewayMessage) {
        val adapterId = requireNotNull(message.destination.adapterId) {
            "GatewayMessage bez wyznaczonego destination.adapterId nie może zostać dostarczony"
        }
        val adapter = requireNotNull(adapters[adapterId]) { "Brak zarejestrowanego adaptera dla $adapterId" }
        adapter.send(message)
    }
}
