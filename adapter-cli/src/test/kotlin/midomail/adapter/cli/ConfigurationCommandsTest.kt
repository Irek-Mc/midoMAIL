package midomail.adapter.cli

import midomail.domain.port.memory.InMemoryConfigurationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza komendy CLI zapisu konfiguracji (SPEC-0024, §3).
 */
class ConfigurationCommandsTest {

    @Test
    fun `config-set stores the value`() {
        val configurationProvider = InMemoryConfigurationProvider()
        val dispatcher = CliDispatcher(ConfigurationCommands(configurationProvider).commands())

        val output = dispatcher.dispatch(arrayOf("config-set", "gateway.instanceId", "midomail-01"))

        assertEquals("OK: gateway.instanceId = midomail-01", output)
        assertEquals("midomail-01", configurationProvider.getValue("gateway.instanceId"))
    }

    @Test
    fun `config-rollback restores the previous value`() {
        val configurationProvider = InMemoryConfigurationProvider()
        val dispatcher = CliDispatcher(ConfigurationCommands(configurationProvider).commands())
        dispatcher.dispatch(arrayOf("config-set", "gateway.instanceId", "v1"))
        dispatcher.dispatch(arrayOf("config-set", "gateway.instanceId", "v2"))

        val output = dispatcher.dispatch(arrayOf("config-rollback", "gateway.instanceId"))

        assertEquals("OK: gateway.instanceId = v1", output)
        assertEquals("v1", configurationProvider.getValue("gateway.instanceId"))
    }

    @Test
    fun `config-rollback with no history returns a readable message`() {
        val dispatcher = CliDispatcher(ConfigurationCommands(InMemoryConfigurationProvider()).commands())

        val output = dispatcher.dispatch(arrayOf("config-rollback", "unknown.key"))

        assertTrue(output.contains("Brak historii"))
    }
}
