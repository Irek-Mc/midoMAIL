package midomail.platform.jvm

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileSecretStoreTest {

    private val path = Files.createTempFile("file-secret-store-test", ".properties").also { it.deleteIfExists() }

    @AfterTest
    fun cleanup() {
        path.deleteIfExists()
    }

    @Test
    fun `write then read round-trips the value through the real file`() {
        val store = FileSecretStore(path)

        store.write("email-primary/credentials", "s3cr3t")

        assertEquals("s3cr3t", store.read("email-primary/credentials"))
    }

    @Test
    fun `read returns null for an unknown reference`() {
        val store = FileSecretStore(path)

        assertNull(store.read("unknown/reference"))
    }

    @Test
    fun `value persists across a new FileSecretStore instance pointed at the same file`() {
        FileSecretStore(path).write("gsm-primary/pin", "1234")

        val reopened = FileSecretStore(path)

        assertEquals("1234", reopened.read("gsm-primary/pin"))
    }

    @Test
    fun `write restricts file permissions to owner-only where POSIX permissions are supported`() {
        val store = FileSecretStore(path)

        store.write("k", "v")

        val view = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView::class.java)
        if (view != null) {
            val permissions = Files.getPosixFilePermissions(path)
            assertEquals(setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), permissions)
        }
    }
}
