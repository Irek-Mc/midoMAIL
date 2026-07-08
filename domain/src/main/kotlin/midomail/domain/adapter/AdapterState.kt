package midomail.domain.adapter

/**
 * Stany cyklu życia adaptera (10-Core/14-Registry-Adapterow.md, §4).
 */
enum class AdapterState {
    REGISTERED,
    INITIALIZING,
    READY,
    BUSY,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}

/**
 * Tabela dozwolonych przejść (SPEC-0014-Adapter-Lifecycle-Contract.md, §Tabela przejść).
 *
 * `STOPPED` jest stanem końcowym. `FAILED` nie jest stanem końcowym — jedyne wyjście z `FAILED`
 * to `STOPPING` (zatrzymanie administracyjne), nie bezpośredni powrót do `READY`.
 */
private val ALLOWED_TRANSITIONS: Map<AdapterState, Set<AdapterState>> = mapOf(
    AdapterState.REGISTERED to setOf(AdapterState.INITIALIZING),
    AdapterState.INITIALIZING to setOf(AdapterState.READY, AdapterState.FAILED),
    AdapterState.READY to setOf(AdapterState.BUSY, AdapterState.DEGRADED, AdapterState.FAILED, AdapterState.STOPPING),
    AdapterState.BUSY to setOf(AdapterState.READY, AdapterState.DEGRADED, AdapterState.FAILED, AdapterState.STOPPING),
    AdapterState.DEGRADED to setOf(AdapterState.READY, AdapterState.BUSY, AdapterState.FAILED, AdapterState.STOPPING),
    AdapterState.FAILED to setOf(AdapterState.STOPPING),
    AdapterState.STOPPING to setOf(AdapterState.STOPPED),
    AdapterState.STOPPED to emptySet()
)

fun AdapterState.canTransitionTo(target: AdapterState): Boolean =
    target in (ALLOWED_TRANSITIONS[this] ?: emptySet())
