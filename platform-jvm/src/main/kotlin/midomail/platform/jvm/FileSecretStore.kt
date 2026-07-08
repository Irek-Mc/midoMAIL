package midomail.platform.jvm

import midomail.domain.port.SecretStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Implementacja plikowa [SecretStore] (ADR-0036-Platforma-JVM-Kompozycja.md, SA-18) — zgodna z
 * `security.secretStore: "file"`, już przewidzianym w SPEC-0005-Configuration-Model.md §5 obok
 * `android-keystore`/`env`.
 *
 * **Jawnie udokumentowane uproszczenie** — plik klucz-wartość w formacie `java.util.Properties`,
 * NIE prawdziwy keystore/vault (bez szyfrowania zawartości pliku). Uprawnienia pliku ustawiane na
 * `600` (tylko właściciel) po każdym zapisie — jedyna zastosowana ochrona. Odnotowane w
 * Architectural Debt Report Fazy 7 (Iteracja 7.9).
 */
class FileSecretStore(private val path: Path) : SecretStore {
    private val lock = ReentrantLock()

    override fun read(reference: String): String? = lock.withLock {
        loadProperties().getProperty(reference)
    }

    override fun write(reference: String, value: String) = lock.withLock {
        val properties = loadProperties()
        properties.setProperty(reference, value)
        path.outputStream().use { properties.store(it, "midomail :platform-jvm secrets") }
        restrictPermissionsIfSupported()
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        if (path.exists()) {
            path.inputStream().use { properties.load(it) }
        }
        return properties
    }

    private fun restrictPermissionsIfSupported() {
        val view = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView::class.java)
        if (view != null) {
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }
}
