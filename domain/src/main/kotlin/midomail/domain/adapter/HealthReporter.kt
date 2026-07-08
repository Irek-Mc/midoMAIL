package midomail.domain.adapter

/**
 * Port zgłaszania stanu adaptera (SPEC-0010-Plugin-SDK-Contract.md, §Porty przekazywane
 * adapterowi). Kierunek odwrotny do [midomail.domain.port.HealthProvider] (który jest
 * odpytywany — „pull") — adapter aktywnie zgłasza zmianę własnego stanu („push"), np.
 * natychmiast po utracie połączenia, bez czekania na najbliższe odpytanie.
 */
interface HealthReporter {
    fun report(adapterId: AdapterId, status: HealthStatus)
}
