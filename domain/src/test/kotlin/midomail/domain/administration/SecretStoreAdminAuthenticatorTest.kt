package midomail.domain.administration

import midomail.domain.port.SecretStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt uwierzytelniania API administracyjnego (ADR-0019).
 */
class SecretStoreAdminAuthenticatorTest {

    private class FakeSecretStore(private val values: MutableMap<String, String> = mutableMapOf()) : SecretStore {
        override fun read(reference: String): String? = values[reference]
        override fun write(reference: String, value: String) {
            values[reference] = value
        }
    }

    @Test
    fun `authenticate succeeds when the provided key matches the stored key`() {
        val secretStore = FakeSecretStore(mutableMapOf("admin-api/key" to "correct-key"))
        val authenticator = SecretStoreAdminAuthenticator(secretStore)

        assertTrue(authenticator.authenticate("correct-key"))
    }

    @Test
    fun `authenticate fails when the provided key does not match`() {
        val secretStore = FakeSecretStore(mutableMapOf("admin-api/key" to "correct-key"))
        val authenticator = SecretStoreAdminAuthenticator(secretStore)

        assertFalse(authenticator.authenticate("wrong-key"))
    }

    @Test
    fun `authenticate fails closed when no key is configured`() {
        val authenticator = SecretStoreAdminAuthenticator(FakeSecretStore())

        assertFalse(authenticator.authenticate("anything"))
    }

    @Test
    fun `authenticate uses the configured secret reference, not a hardcoded one`() {
        val secretStore = FakeSecretStore(mutableMapOf("custom/reference" to "custom-key"))
        val authenticator = SecretStoreAdminAuthenticator(secretStore, secretReference = "custom/reference")

        assertTrue(authenticator.authenticate("custom-key"))
    }

    @Test
    fun `an empty provided key never matches a non-empty stored key`() {
        val secretStore = FakeSecretStore(mutableMapOf("admin-api/key" to "correct-key"))
        val authenticator = SecretStoreAdminAuthenticator(secretStore)

        assertFalse(authenticator.authenticate(""))
    }
}
