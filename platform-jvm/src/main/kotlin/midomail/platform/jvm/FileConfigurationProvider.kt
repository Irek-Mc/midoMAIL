package midomail.platform.jvm

import midomail.domain.port.ConfigurationProvider
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Implementacja plikowa [ConfigurationProvider], persystująca wartości i historię na dysku
 * (ADR-0036-Platforma-JVM-Kompozycja.md, SA-18) — NIE `InMemoryConfigurationProvider`, żeby
 * konfiguracja (w tym pełny dokument YAML pod kluczem `gateway.fullConfiguration`, ADR-0032)
 * przetrwała restart procesu `:platform-jvm`. Domyka rekomendację z raportu Fazy 5 („rozważyć
 * trwałą historię konfiguracji").
 *
 * Format: `java.util.Properties` (obsługuje poprawnie wieloliniowe/dowolne wartości tekstowe przez
 * własne escapowanie — bezpieczne nawet dla całego dokumentu YAML jako jednej wartości). Historia
 * przechowywana pod kluczami pomocniczymi `<key>.__history_count`/`<key>.__history_<N>` — ta sama
 * semantyka co [midomail.domain.port.memory.InMemoryConfigurationProvider] (najnowsza wpisana
 * jako pierwsza, `rollback` zdejmuje z historii).
 */
class FileConfigurationProvider(private val path: Path) : ConfigurationProvider {
    private val lock = ReentrantLock()

    override fun getValue(key: String): String? = lock.withLock {
        loadProperties().getProperty(key)
    }

    override fun setValue(key: String, value: String) = lock.withLock {
        val properties = loadProperties()
        properties.getProperty(key)?.let { previous ->
            pushHistory(properties, key, previous)
        }
        properties.setProperty(key, value)
        save(properties)
    }

    override fun history(key: String): List<String> = lock.withLock {
        readHistory(loadProperties(), key)
    }

    override fun rollback(key: String): String? = lock.withLock {
        val properties = loadProperties()
        val historyEntries = readHistory(properties, key).toMutableList()
        if (historyEntries.isEmpty()) return@withLock null
        val restored = historyEntries.removeAt(0)
        properties.setProperty(key, restored)
        writeHistory(properties, key, historyEntries)
        save(properties)
        restored
    }

    private fun pushHistory(properties: Properties, key: String, previousValue: String) {
        val historyEntries = readHistory(properties, key).toMutableList()
        historyEntries.add(0, previousValue)
        writeHistory(properties, key, historyEntries)
    }

    private fun readHistory(properties: Properties, key: String): List<String> {
        val count = properties.getProperty("$key.__history_count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).map { index -> properties.getProperty("$key.__history_$index") ?: "" }
    }

    private fun writeHistory(properties: Properties, key: String, entries: List<String>) {
        properties.setProperty("$key.__history_count", entries.size.toString())
        entries.forEachIndexed { index, entry -> properties.setProperty("$key.__history_$index", entry) }
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        if (path.exists()) {
            path.inputStream().use { properties.load(it) }
        }
        return properties
    }

    private fun save(properties: Properties) {
        path.outputStream().use { properties.store(it, "midomail :platform-jvm configuration") }
    }
}
