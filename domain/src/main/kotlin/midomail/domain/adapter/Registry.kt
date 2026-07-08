package midomail.domain.adapter

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventPublisher
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Ładunek zdarzenia publikowanego przy każdym przejściu stanu adaptera. */
data class AdapterStateChanged(val adapterId: AdapterId, val state: AdapterState)

/**
 * Registry Adapterów (10-Core/14-Registry-Adapterow.md; SPEC-0010-Plugin-SDK-Contract.md,
 * §Przepływ rejestracji i konfiguracji; SPEC-0014-Adapter-Lifecycle-Contract.md).
 *
 * [register] przeprowadza adapter przez happy path Registered → Initializing → Ready, wywołując
 * [AdapterLifecycle.start] podczas przejścia Initializing → Ready (SPEC-0010). [stop] jest
 * symetryczne: Stopping → Stopped, wywołując [AdapterLifecycle.stop]. [transitionTo] wymusza
 * dowolne inne przejście dozwolone przez [AdapterState.canTransitionTo] (np. Ready → Degraded).
 *
 * Każde przejście stanu jest publikowane jako zdarzenie domenowe (10-Core/14-Registry-Adapterow.md,
 * §4; 10-Core/15-Event-Bus.md). Wszystkie zdarzenia jednego adaptera współdzielą ten sam
 * CorrelationId, nadany przy rejestracji — grupuje historię jednej sesji pracy adaptera.
 *
 * Jeśli [AdapterLifecycle.start] rzuci wyjątek, [register] wykonuje przejście
 * `Initializing → Failed` (ADR-0014-Registry-Start-Failure.md; SPEC-0014, §Tabela przejść) przed
 * ponownym rzuceniem wyjątku — wywołujący nadal wie, że rejestracja się nie powiodła, ale stan
 * Registry i zdarzenia domenowe poprawnie odzwierciedlają awarię zamiast wyjątku niszczącego
 * proces bez śladu.
 */
class Registry(
    private val eventPublisher: EventPublisher
) {
    private val states = ConcurrentHashMap<AdapterId, AdapterState>()
    private val adapters = ConcurrentHashMap<AdapterId, AdapterLifecycle>()
    private val correlationIds = ConcurrentHashMap<AdapterId, CorrelationId>()

    fun register(adapterId: AdapterId, adapter: AdapterLifecycle) {
        require(!states.containsKey(adapterId)) { "Adapter $adapterId jest już zarejestrowany" }
        adapters[adapterId] = adapter
        correlationIds[adapterId] = CorrelationId(UUID.randomUUID().toString())
        setState(adapterId, AdapterState.REGISTERED)
        setState(adapterId, AdapterState.INITIALIZING)
        try {
            adapter.start()
        } catch (exception: Exception) {
            setState(adapterId, AdapterState.FAILED)
            throw exception
        }
        setState(adapterId, AdapterState.READY)
    }

    fun stop(adapterId: AdapterId) {
        val adapter = adapters.getValue(adapterId)
        transitionTo(adapterId, AdapterState.STOPPING)
        adapter.stop()
        transitionTo(adapterId, AdapterState.STOPPED)
    }

    fun unregister(adapterId: AdapterId) {
        val current = states[adapterId]
        require(current == AdapterState.STOPPED) {
            "Wyrejestrowanie wymaga stanu STOPPED, aktualny stan adaptera $adapterId: $current"
        }
        states.remove(adapterId)
        adapters.remove(adapterId)
        correlationIds.remove(adapterId)
    }

    fun transitionTo(adapterId: AdapterId, target: AdapterState) {
        val current = states[adapterId] ?: error("Adapter $adapterId nie jest zarejestrowany")
        require(current.canTransitionTo(target)) {
            "Niedozwolone przejście z $current do $target dla adaptera $adapterId"
        }
        setState(adapterId, target)
    }

    fun stateOf(adapterId: AdapterId): AdapterState? = states[adapterId]

    /**
     * Zbiór aktualnie znanych `Registry` adapterów (ADR-0018-Registry-Enumeracja.md) — od
     * `register()` do `unregister()`, niezależnie od bieżącego `AdapterState`. Nie zwraca instancji
     * `Adapter`/`AdapterLifecycle` — ta granica pozostaje nienaruszona (patrz ADR-0018, §Decyzja).
     */
    fun registeredAdapterIds(): Set<AdapterId> = states.keys.toSet()

    private fun setState(adapterId: AdapterId, state: AdapterState) {
        states[adapterId] = state
        eventPublisher.publish(
            Event(
                eventId = EventId(UUID.randomUUID().toString()),
                eventType = EventType("adapter.state_changed"),
                eventVersion = EventVersion("1.0"),
                category = EventCategory.ADAPTER,
                timestamp = Instant.now(),
                correlationId = correlationIds.getValue(adapterId),
                sourceComponent = SourceComponent("Registry"),
                payload = AdapterStateChanged(adapterId, state)
            )
        )
    }
}
