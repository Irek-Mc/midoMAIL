package midomail.platform.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test instrumentowany — uruchamiany na rzeczywistym urządzeniu (`connectedDebugAndroidTest`),
 * nie Robolectric. Potwierdza kontrakt [AndroidKeystoreSecretStore] (SPEC-0017-Secret-Store-Contract.md)
 * na prawdziwym Android Keystore System.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val secretStore = AndroidKeystoreSecretStore(context)

    @Test
    fun readReturnsNullForUnknownReference() {
        assertNull(secretStore.read("nieznana-referencja-${System.nanoTime()}"))
    }

    @Test
    fun writeThenReadReturnsTheSameValue() {
        val reference = "test/sekret-${System.nanoTime()}"

        secretStore.write(reference, "tajne-haslo-123")

        assertEquals("tajne-haslo-123", secretStore.read(reference))
    }

    @Test
    fun writeTwiceOverwritesThePreviousValue() {
        val reference = "test/nadpisanie-${System.nanoTime()}"

        secretStore.write(reference, "pierwsza-wartosc")
        secretStore.write(reference, "druga-wartosc")

        assertEquals("druga-wartosc", secretStore.read(reference))
    }

    @Test
    fun secretIsNotStoredAsPlainTextInSharedPreferences() {
        val reference = "test/szyfrowanie-${System.nanoTime()}"
        secretStore.write(reference, "TAJNA-WARTOSC-DO-WYSZUKANIA")

        val preferences = context.getSharedPreferences("midomail_secret_store", android.content.Context.MODE_PRIVATE)
        val rawStoredValue = preferences.getString(reference, null)

        assertFalse(rawStoredValue?.contains("TAJNA-WARTOSC-DO-WYSZUKANIA") ?: false)
    }
}
