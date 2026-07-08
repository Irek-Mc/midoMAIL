package midomail.platform.jvm

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileConfigurationProviderTest {

    private val path = Files.createTempFile("file-configuration-provider-test", ".properties").also { it.deleteIfExists() }

    @AfterTest
    fun cleanup() {
        path.deleteIfExists()
    }

    @Test
    fun `setValue then getValue round-trips through the real file`() {
        val provider = FileConfigurationProvider(path)

        provider.setValue("gateway.instanceId", "midomail-01")

        assertEquals("midomail-01", provider.getValue("gateway.instanceId"))
    }

    @Test
    fun `history records the previous value, newest first, and rollback restores it`() {
        val provider = FileConfigurationProvider(path)
        provider.setValue("gateway.instanceId", "midomail-01")

        provider.setValue("gateway.instanceId", "midomail-02")

        assertEquals(listOf("midomail-01"), provider.history("gateway.instanceId"))

        val restored = provider.rollback("gateway.instanceId")

        assertEquals("midomail-01", restored)
        assertEquals("midomail-01", provider.getValue("gateway.instanceId"))
        assertEquals(emptyList(), provider.history("gateway.instanceId"))
    }

    @Test
    fun `rollback on a key with no history returns null`() {
        val provider = FileConfigurationProvider(path)

        assertNull(provider.rollback("never.set"))
    }

    @Test
    fun `values and history survive a restart - a new instance pointed at the same file sees them`() {
        val first = FileConfigurationProvider(path)
        first.setValue("gateway.instanceId", "midomail-01")
        first.setValue("gateway.instanceId", "midomail-02")

        val reopened = FileConfigurationProvider(path)

        assertEquals("midomail-02", reopened.getValue("gateway.instanceId"))
        assertEquals(listOf("midomail-01"), reopened.history("gateway.instanceId"))
    }

    @Test
    fun `a multi-line value (whole YAML document) round-trips correctly`() {
        val provider = FileConfigurationProvider(path)
        val yamlDocument = "gateway:\n  instanceId: \"midomail-01\"\nrouting:\n  rules: []\n"

        provider.setValue("gateway.fullConfiguration", yamlDocument)

        assertEquals(yamlDocument, provider.getValue("gateway.fullConfiguration"))
    }
}
