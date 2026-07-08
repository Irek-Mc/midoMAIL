package midomail.domain.administration

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import java.util.concurrent.ConcurrentHashMap

/**
 * Mutowalny, współdzielony widok żywych instancji [Adapter] (SPEC-0024, §Odczyt stanu: „Registry
 * sam nie przechowuje... odczyt wymaga żywej instancji Adapter") — używany przez endpointy
 * REST (Iteracja 5.10/5.11) ORAZ komendy CLI (Iteracja 5.14/5.15), żeby operacja usuń/restart
 * wykonana przez jeden adapter administracyjny była natychmiast widoczna w drugim (dowód, że oba
 * są cienkimi klientami tych samych portów Core, nie rozbieżnymi implementacjami).
 *
 * Umieszczony w `:domain` (nie w `:adapter-rest`, gdzie pierwotnie powstał w Iteracji 5.10) właśnie
 * dlatego — `:adapter-rest` i `:adapter-cli` są siostrzanymi modułami, żaden nie zależy od
 * drugiego, więc współdzielony stan musi żyć w ich wspólnej zależności.
 *
 * Ten sam duch co `AdapterRegistryOutbound` w `:platform-android` (Faza 3) — mały, mutowalny
 * komponent kompozycyjny łączący `AdapterId` z konkretną instancją, bo `Registry` sam tego nie robi.
 */
class ManagedAdapters(initial: List<Adapter> = emptyList()) {

    private val adapters = ConcurrentHashMap<AdapterId, Adapter>()

    init {
        initial.forEach { adapters[it.adapterId] = it }
    }

    fun all(): List<Adapter> = adapters.values.toList()

    fun get(adapterId: AdapterId): Adapter? = adapters[adapterId]

    fun remove(adapterId: AdapterId) {
        adapters.remove(adapterId)
    }
}
