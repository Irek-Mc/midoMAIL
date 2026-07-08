package midomail.domain.port.memory

import midomail.domain.port.ConfigurationProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Referencyjna implementacja [ConfigurationProvider] w pamięci (SPEC-0012-Configuration-Provider-Contract.md;
 * zapis/historia/rollback z ADR-0020-Konfiguracja-Zapis.md).
 */
class InMemoryConfigurationProvider(
    initialValues: Map<String, String> = emptyMap()
) : ConfigurationProvider {

    private val values = ConcurrentHashMap(initialValues)
    private val history = ConcurrentHashMap<String, MutableList<String>>()

    override fun getValue(key: String): String? = values[key]

    override fun setValue(key: String, value: String) {
        values[key]?.let { previous ->
            history.computeIfAbsent(key) { mutableListOf() }.add(0, previous)
        }
        values[key] = value
    }

    override fun history(key: String): List<String> = history[key]?.toList() ?: emptyList()

    override fun rollback(key: String): String? {
        val keyHistory = history[key] ?: return null
        if (keyHistory.isEmpty()) return null
        val restored = keyHistory.removeAt(0)
        values[key] = restored
        return restored
    }
}
