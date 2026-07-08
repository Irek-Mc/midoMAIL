package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.ConfigEntryDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.port.memory.InMemoryConfigurationProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Potwierdza endpointy zapisu konfiguracji (SPEC-0024, §3) na prawdziwym serwerze/kliencie.
 */
class ConfigurationEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(configurationProvider: InMemoryConfigurationProvider = InMemoryConfigurationProvider()): InMemoryConfigurationProvider {
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ConfigurationEndpoints(configurationProvider).registerRoutes(server)
        server.start()
        return configurationProvider
    }

    private fun postWithBody(path: String, body: String): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to responseBody
    }

    @Test
    fun `POST config sets the value and it is reflected in getValue`() {
        val configurationProvider = setup()

        val (statusCode, body) = postWithBody("/config?key=gateway.instanceId", "midomail-01")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<ConfigEntryDto>(body)
        assertEquals("midomail-01", dto.value)
        assertEquals("midomail-01", configurationProvider.getValue("gateway.instanceId"))
    }

    @Test
    fun `a second POST config records the previous value in history`() {
        val configurationProvider = setup()
        postWithBody("/config?key=gateway.instanceId", "v1")

        val (_, body) = postWithBody("/config?key=gateway.instanceId", "v2")

        val dto = json.decodeFromString<ConfigEntryDto>(body)
        assertEquals("v2", dto.value)
        assertEquals(listOf("v1"), dto.history)
    }

    @Test
    fun `POST config_rollback restores the most recent previous value`() {
        val configurationProvider = setup()
        postWithBody("/config?key=gateway.instanceId", "v1")
        postWithBody("/config?key=gateway.instanceId", "v2")

        val (statusCode, body) = postWithBody("/config/rollback?key=gateway.instanceId", "")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<ConfigEntryDto>(body)
        assertEquals("v1", dto.value)
        assertEquals("v1", configurationProvider.getValue("gateway.instanceId"))
    }

    @Test
    fun `POST config_rollback with no history returns 409`() {
        setup()

        val (statusCode, _) = postWithBody("/config/rollback?key=unknown.key", "")

        assertEquals(409, statusCode)
    }

    @Test
    fun `POST config without a key query parameter returns 400`() {
        setup()

        val (statusCode, _) = postWithBody("/config", "value")

        assertEquals(400, statusCode)
    }
}
