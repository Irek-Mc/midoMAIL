package midomail.platform.android

import midomail.domain.port.SecretStore
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailHostPortResolverTest {

    private class FakeSecretStore(private val values: Map<String, String> = emptyMap()) : SecretStore {
        override fun read(reference: String): String? = values[reference]
        override fun write(reference: String, value: String) {
            error("nieużywane w tym teście")
        }
    }

    @Test
    fun `falls back to Gmail defaults when neither key is set`() {
        val resolved = resolveHostPort(
            FakeSecretStore(),
            hostKey = "email-primary/smtp-host",
            portKey = "email-primary/smtp-port",
            defaultHost = "smtp.gmail.com",
            defaultPort = 587
        )

        assertEquals(HostPort("smtp.gmail.com", 587), resolved)
    }

    @Test
    fun `uses stored values when both keys are set`() {
        val secretStore = FakeSecretStore(
            mapOf("email-primary/imap-host" to "imap.example.com", "email-primary/imap-port" to "1993")
        )

        val resolved = resolveHostPort(
            secretStore,
            hostKey = "email-primary/imap-host",
            portKey = "email-primary/imap-port",
            defaultHost = "imap.gmail.com",
            defaultPort = 993
        )

        assertEquals(HostPort("imap.example.com", 1993), resolved)
    }

    @Test
    fun `falls back to default port when the stored port is not a valid integer`() {
        val secretStore = FakeSecretStore(mapOf("email-primary/smtp-port" to "not-a-number"))

        val resolved = resolveHostPort(
            secretStore,
            hostKey = "email-primary/smtp-host",
            portKey = "email-primary/smtp-port",
            defaultHost = "smtp.gmail.com",
            defaultPort = 587
        )

        assertEquals(587, resolved.port)
    }
}
