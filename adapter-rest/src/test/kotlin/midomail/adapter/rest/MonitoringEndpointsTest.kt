package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.ResourceSnapshotDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.monitoring.ResourceMonitor
import midomail.domain.monitoring.ResourceSnapshot
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonitoringEndpointsTest {

    private lateinit var server: AdminHttpServer

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    private class FakeResourceMonitor(private val snapshot: ResourceSnapshot) : ResourceMonitor {
        override fun snapshot(): ResourceSnapshot = snapshot
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(resourceMonitor: ResourceMonitor) {
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        MonitoringEndpoints(resourceMonitor).registerRoutes(server)
        server.start()
    }

    private fun get(path: String): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        val responseCode = connection.responseCode
        val body = connection.inputStream.readBytes().toString(Charsets.UTF_8)
        return responseCode to body
    }

    @Test
    fun `GET monitoring_resources returns the current snapshot`() {
        setup(FakeResourceMonitor(ResourceSnapshot(ramUsedBytes = 100, ramTotalBytes = 200)))

        val (statusCode, body) = get("/monitoring/resources")

        assertEquals(200, statusCode)
        val dto = Json.decodeFromString<ResourceSnapshotDto>(body)
        assertEquals(100, dto.ramUsedBytes)
        assertEquals(200, dto.ramTotalBytes)
    }

    @Test
    fun `GET monitoring_resources reports unavailable metrics as null, not zero`() {
        setup(FakeResourceMonitor(ResourceSnapshot()))

        val (_, body) = get("/monitoring/resources")

        val dto = Json.decodeFromString<ResourceSnapshotDto>(body)
        assertNull(dto.cpuUsagePercent)
    }
}
