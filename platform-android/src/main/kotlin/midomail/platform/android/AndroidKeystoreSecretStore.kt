package midomail.platform.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import midomail.domain.port.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Implementacja [SecretStore] przez Android Keystore System (SPEC-0017-Secret-Store-Contract.md;
 * 30-Infrastructure/30-Konfiguracja.md, §5; 30-Infrastructure/31-Bezpieczenstwo.md).
 *
 * Android Keystore przechowuje wyłącznie klucze kryptograficzne, nie dowolne dane — każda
 * [reference] dostaje własny klucz AES (alias = referencja), którym szyfrowana jest rzeczywista
 * wartość sekretu przed zapisem w `SharedPreferences`. Klucz nigdy nie opuszcza zaufanego
 * środowiska systemowego/sprzętowego urządzenia — szyfrogram i IV, nie klucz, trafiają do
 * `SharedPreferences`.
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun read(reference: String): String? {
        val encoded = preferences.getString(reference, null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, requireKey(reference), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    override fun write(reference: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(reference))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        preferences.edit().putString(reference, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    private fun requireKey(reference: String): SecretKey =
        keyStore.getKey(reference, null) as? SecretKey
            ?: error("Brak klucza Keystore dla referencji '$reference' — wpis w SharedPreferences bez odpowiadającego klucza")

    private fun getOrCreateKey(reference: String): SecretKey {
        (keyStore.getKey(reference, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(reference, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "midomail_secret_store"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
