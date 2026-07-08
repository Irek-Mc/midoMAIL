package midomail.adapter.cli

import midomail.domain.administration.AdapterConfigurationAdministration
import midomail.domain.port.memory.InMemoryConfigurationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdapterConfigurationCommandsTest {

    private fun setup(): CliDispatcher =
        CliDispatcher(AdapterConfigurationCommands(AdapterConfigurationAdministration(InMemoryConfigurationProvider())).commands())

    @Test
    fun `adapter-config-set then adapter-config round-trips a non-secret field`() {
        val dispatcher = setup()
        dispatcher.dispatch(arrayOf("adapter-config-set", "email-primary", "email", "smtp.host", "smtp.example.com"))

        val output = dispatcher.dispatch(arrayOf("adapter-config", "email-primary", "email"))

        assertTrue(output.contains("smtp.host = smtp.example.com"))
    }

    @Test
    fun `adapter-config never shows a secret field's value`() {
        val dispatcher = setup()
        dispatcher.dispatch(arrayOf("adapter-config-set", "email-primary", "email", "smtp.password", "super-secret"))

        val output = dispatcher.dispatch(arrayOf("adapter-config", "email-primary", "email"))

        assertTrue(output.contains("smtp.password = (brak/sekretne)"))
    }

    @Test
    fun `adapter-config-set rejects a field outside the adapter type's schema`() {
        val dispatcher = setup()

        val output = dispatcher.dispatch(arrayOf("adapter-config-set", "email-primary", "email", "maxSmsSegments", "5"))

        assertTrue(output.contains("nie należy do schematu"))
    }

    @Test
    fun `adapter-config for an unknown adapter type reports so`() {
        val dispatcher = setup()

        val output = dispatcher.dispatch(arrayOf("adapter-config", "unknown-primary", "unknown"))

        assertEquals("Nieznany typ adaptera: unknown", output)
    }
}
