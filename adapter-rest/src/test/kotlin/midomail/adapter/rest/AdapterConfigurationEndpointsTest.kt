package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AdapterConfigurationDto
import midomail.domain.administration.AdapterConfigurationAdministration
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.port.memory.InMemoryConfigurationProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdapterConfigurationEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(): AdapterConfigurationAdministration {
        val administration = AdapterConfigurationAdministration(InMemoryConfigurationProvider())
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        AdapterConfigurationEndpoints(administration).registerRoutes(server)
        server.start()
        return administration
    }

    private fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        if (body != null || method != "GET") {
            connection.doOutput = true
            connection.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
        }
        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to responseBody
    }

    @Test
    fun `POST then GET adapters_configuration round-trips a non-secret field`() {
        setup()
        request("POST", "/adapters/configuration?id=email-primary&type=email&field=smtp.host", "smtp.example.com")

        val (statusCode, body) = request("GET", "/adapters/configuration?id=email-primary&type=email")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<AdapterConfigurationDto>(body)
        assertEquals("smtp.example.com", dto.fields["smtp.host"])
    }

    @Test
    fun `GET never returns a value for a secret field`() {
        setup()
        request("POST", "/adapters/configuration?id=email-primary&type=email&field=smtp.password", "super-secret")

        val (_, body) = request("GET", "/adapters/configuration?id=email-primary&type=email")

        val dto = json.decodeFromString<AdapterConfigurationDto>(body)
        assertNull(dto.fields["smtp.password"])
    }

    @Test
    fun `POST rejects a field that does not belong to the adapter type's schema`() {
        setup()

        val (statusCode, _) = request("POST", "/adapters/configuration?id=email-primary&type=email&field=maxSmsSegments", "5")

        assertEquals(400, statusCode)
    }

    @Test
    fun `GET without required query parameters returns 400`() {
        setup()

        val (statusCode, _) = request("GET", "/adapters/configuration?id=email-primary")

        assertEquals(400, statusCode)
    }
}
