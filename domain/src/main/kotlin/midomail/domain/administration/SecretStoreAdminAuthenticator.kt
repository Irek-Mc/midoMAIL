package midomail.domain.administration

import midomail.domain.port.SecretStore
import java.security.MessageDigest

/**
 * Referencyjna implementacja [AdminAuthenticator] (ADR-0019) — czyta oczekiwany klucz z
 * [SecretStore] pod ustaloną referencją, porównuje w czasie stałym ([MessageDigest.isEqual],
 * ta sama funkcja biblioteczna użyta już w projekcie do porównań kryptograficznych, np.
 * `SmsMessageMapper`'s SHA-256 — ochrona przed atakiem czasowym na porównanie ciągów).
 *
 * Brak skonfigurowanego klucza (referencja nie znaleziona w [SecretStore]) oznacza, że
 * uwierzytelnienie zawsze się nie powiedzie — nie jest to błąd, tylko brak dostępu domyślnie
 * bezpieczny (fail-closed).
 */
class SecretStoreAdminAuthenticator(
    private val secretStore: SecretStore,
    private val secretReference: String = "admin-api/key"
) : AdminAuthenticator {

    override fun authenticate(providedKey: String): Boolean {
        val expectedKey = secretStore.read(secretReference) ?: return false
        return MessageDigest.isEqual(expectedKey.toByteArray(Charsets.UTF_8), providedKey.toByteArray(Charsets.UTF_8))
    }
}
