package midomail.platform.jvm

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.message.GatewayMessage
import midomail.domain.port.GatewayOutbound
import java.util.concurrent.ConcurrentHashMap

/**
 * `GatewayOutbound` wielo-adapterowy — identyczna odpowiedzialność co odpowiednik w
 * `:platform-android` (ADR-0036, „Migracja `:platform-android` na wspólny kod kompozycyjny z
 * `:platform-jvm` — poza zakresem"; obie platformy pozostają niezależnymi punktami kompozycji).
 * Duplikacja tej małej (12 linii) klasy jest świadoma, nie przeoczenie.
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
