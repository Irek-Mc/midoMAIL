package midomail.domain.port.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryConfigurationProviderTest {

    @Test
    fun `getValue returns null for an unknown key`() {
        val provider = InMemoryConfigurationProvider()

        assertNull(provider.getValue("gateway.instanceId"))
    }

    @Test
    fun `getValue returns the configured value for a known key`() {
        val provider = InMemoryConfigurationProvider(mapOf("gateway.instanceId" to "midomail-01"))

        assertEquals("midomail-01", provider.getValue("gateway.instanceId"))
    }

    /**
     * Potwierdza ADR-0020-Konfiguracja-Zapis.md — dodane w Iteracji 5.3 (Faza 5).
     */
    @Test
    fun `setValue overwrites getValue`() {
        val provider = InMemoryConfigurationProvider()

        provider.setValue("gateway.instanceId", "midomail-01")

        assertEquals("midomail-01", provider.getValue("gateway.instanceId"))
    }

    @Test
    fun `setValue on a key with no prior value does not record any history`() {
        val provider = InMemoryConfigurationProvider()

        provider.setValue("gateway.instanceId", "midomail-01")

        assertTrue(provider.history("gateway.instanceId").isEmpty())
    }

    @Test
    fun `history retains previous values, most recent first`() {
        val provider = InMemoryConfigurationProvider()
        provider.setValue("gateway.instanceId", "v1")
        provider.setValue("gateway.instanceId", "v2")
        provider.setValue("gateway.instanceId", "v3")

        assertEquals(listOf("v2", "v1"), provider.history("gateway.instanceId"))
    }

    @Test
    fun `rollback restores the most recent previous value and removes it from history`() {
        val provider = InMemoryConfigurationProvider()
        provider.setValue("gateway.instanceId", "v1")
        provider.setValue("gateway.instanceId", "v2")

        val restored = provider.rollback("gateway.instanceId")

        assertEquals("v1", restored)
        assertEquals("v1", provider.getValue("gateway.instanceId"))
        assertTrue(provider.history("gateway.instanceId").isEmpty())
    }

    @Test
    fun `rollback returns null when there is no history to restore`() {
        val provider = InMemoryConfigurationProvider(mapOf("gateway.instanceId" to "v1"))

        assertNull(provider.rollback("gateway.instanceId"))
        assertEquals("v1", provider.getValue("gateway.instanceId"), "Bieżąca wartość pozostaje niezmieniona, jeśli nie ma czego przywrócić")
    }

    @Test
    fun `two successive rollbacks restore two prior values in order`() {
        val provider = InMemoryConfigurationProvider()
        provider.setValue("gateway.instanceId", "v1")
        provider.setValue("gateway.instanceId", "v2")
        provider.setValue("gateway.instanceId", "v3")

        assertEquals("v2", provider.rollback("gateway.instanceId"))
        assertEquals("v1", provider.rollback("gateway.instanceId"))
        assertNull(provider.rollback("gateway.instanceId"))
    }
}
