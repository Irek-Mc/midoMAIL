package midomail.domain.port

/**
 * Port oceny gotowości/dostępności komponentu (30-Infrastructure/35-Health-Monitor.md).
 */
interface HealthProvider {
    fun health(): Boolean
}
