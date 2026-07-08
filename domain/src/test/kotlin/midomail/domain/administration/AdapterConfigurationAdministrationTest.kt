package midomail.domain.administration

import midomail.domain.adapter.AdapterId
import midomail.domain.port.memory.InMemoryConfigurationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza `AdapterConfigurationAdministration` (ADR-0031).
 */
class AdapterConfigurationAdministrationTest {

    @Test
    fun `write then read returns the value for a non-secret field`() {
        val configurationProvider = InMemoryConfigurationProvider()
        val administration = AdapterConfigurationAdministration(configurationProvider)

        administration.write(AdapterId("email-primary"), "email", "smtp.host", "smtp.example.com")

        assertEquals("smtp.example.com", administration.read(AdapterId("email-primary"), "email")["smtp.host"])
    }

    @Test
    fun `read never returns a value for a secret field, even after write`() {
        val configurationProvider = InMemoryConfigurationProvider()
        val administration = AdapterConfigurationAdministration(configurationProvider)

        administration.write(AdapterId("email-primary"), "email", "smtp.password", "super-secret")

        assertNull(administration.read(AdapterId("email-primary"), "email")["smtp.password"])
    }

    @Test
    fun `read returns every field of the schema for the adapter type, including unset ones`() {
        val administration = AdapterConfigurationAdministration(InMemoryConfigurationProvider())

        val fields = administration.read(AdapterId("email-primary"), "email")

        assertTrue(fields.containsKey("smtp.host"))
        assertTrue(fields.containsKey("imap.folder"))
        assertNull(fields["smtp.host"])
    }

    @Test
    fun `read for the gsm adapter type returns only gsm fields`() {
        val administration = AdapterConfigurationAdministration(InMemoryConfigurationProvider())

        val fields = administration.read(AdapterId("gsm-primary"), "gsm")

        assertEquals(setOf("maxSmsSegments", "forwardToAddress"), fields.keys)
    }

    @Test
    fun `read for an unknown adapter type returns an empty map`() {
        val administration = AdapterConfigurationAdministration(InMemoryConfigurationProvider())

        assertTrue(administration.read(AdapterId("unknown-primary"), "unknown").isEmpty())
    }

    @Test
    fun `write rejects a field that does not belong to the adapter type's schema`() {
        val administration = AdapterConfigurationAdministration(InMemoryConfigurationProvider())

        assertFailsWith<IllegalArgumentException> {
            administration.write(AdapterId("email-primary"), "email", "maxSmsSegments", "5")
        }
    }

    @Test
    fun `configuration for two different adapters does not collide`() {
        val configurationProvider = InMemoryConfigurationProvider()
        val administration = AdapterConfigurationAdministration(configurationProvider)

        administration.write(AdapterId("email-primary"), "email", "smtp.host", "primary.example.com")
        administration.write(AdapterId("email-secondary"), "email", "smtp.host", "secondary.example.com")

        assertEquals("primary.example.com", administration.read(AdapterId("email-primary"), "email")["smtp.host"])
        assertEquals("secondary.example.com", administration.read(AdapterId("email-secondary"), "email")["smtp.host"])
    }
}
