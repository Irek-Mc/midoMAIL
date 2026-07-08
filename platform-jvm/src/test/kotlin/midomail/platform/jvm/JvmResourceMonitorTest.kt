package midomail.platform.jvm

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class JvmResourceMonitorTest {

    private lateinit var dataDirectory: java.nio.file.Path

    @BeforeTest
    fun createDataDirectory() {
        dataDirectory = Files.createTempDirectory("jvm-resource-monitor-test")
    }

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(dataDirectory)
    }

    @Test
    fun `snapshot reports real RAM and storage figures from this machine`() {
        val monitor = JvmResourceMonitor(dataDirectory)

        val snapshot = monitor.snapshot()

        assertTrue(snapshot.ramTotalBytes != null && snapshot.ramTotalBytes!! > 0)
        assertTrue(snapshot.ramUsedBytes != null && snapshot.ramUsedBytes!! in 0..snapshot.ramTotalBytes!!)
        assertTrue(snapshot.storageTotalBytes != null && snapshot.storageTotalBytes!! > 0)
    }

    @Test
    fun `network fields are explicitly null - no portable JDK API for host network counters`() {
        val monitor = JvmResourceMonitor(dataDirectory)

        val snapshot = monitor.snapshot()

        assertTrue(snapshot.networkBytesReceived == null)
        assertTrue(snapshot.networkBytesSent == null)
    }
}
