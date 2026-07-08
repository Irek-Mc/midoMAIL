package midomail.domain.port.memory

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryHealthProviderTest {

    @Test
    fun `health defaults to true`() {
        val provider = InMemoryHealthProvider()

        assertTrue(provider.health())
    }

    @Test
    fun `health reflects the configured value`() {
        val provider = InMemoryHealthProvider(healthy = false)

        assertFalse(provider.health())
    }
}
