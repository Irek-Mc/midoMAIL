package midomail.domain.port.memory

import midomail.domain.port.HealthProvider

/** Referencyjna implementacja [HealthProvider] (30-Infrastructure/35-Health-Monitor.md). */
class InMemoryHealthProvider(private val healthy: Boolean = true) : HealthProvider {

    override fun health(): Boolean = healthy
}
