package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.ValidationResultDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.configuration.AdapterConfigEntry
import midomail.domain.configuration.ConfigurationDocument
import midomail.domain.configuration.ConfigurationValidator
import midomail.domain.configuration.GatewayConfig
import midomail.domain.configuration.MessageStoreConfig
import midomail.domain.configuration.MonitoringConfig
import midomail.domain.configuration.NotificationsConfig
import midomail.domain.configuration.RoutingConfig
import midomail.domain.configuration.SchedulerConfig
import midomail.domain.configuration.SecurityConfig
import midomail.domain.port.memory.InMemoryConfigurationProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `ConfigurationYamlEndpoints` (SPEC-0025, ADR-0032) na prawdziwym serwerze/kliencie,
 * prawdziwym kodeku YAML i prawdziwym walidatorze.
 */
class ConfigurationYamlEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }
    private val codec = YamlConfigurationCodec()

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(): InMemoryConfigurationProvider {
        val configurationProvider = InMemoryConfigurationProvider()
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        ConfigurationYamlEndpoints(configurationProvider, codec, ConfigurationValidator(), platformProfile = "android").registerRoutes(server)
        server.start()
        return configurationProvider
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

    private fun validDocument(): ConfigurationDocument = ConfigurationDocument(
        version = "2.0",
        gateway = GatewayConfig(instanceId = "midomail-01", logLevel = "INFO"),
        routing = RoutingConfig(),
        adapters = listOf(AdapterConfigEntry(adapterId = "email-primary", type = "email", enabled = false)),
        scheduler = SchedulerConfig(),
        security = SecurityConfig(secretStore = "android-keystore"),
        monitoring = MonitoringConfig(healthCheckIntervalSeconds = 30),
        messageStore = MessageStoreConfig(retentionDays = 30, deduplicationRetentionDays = 365),
        notifications = NotificationsConfig()
    )

    @Test
    fun `POST config_yaml_validate reports a valid document as valid`() {
        setup()

        val (statusCode, body) = request("POST", "/config/yaml/validate", codec.encode(validDocument()))

        assertEquals(200, statusCode)
        assertTrue(json.decodeFromString<ValidationResultDto>(body).valid)
    }

    @Test
    fun `POST config_yaml_validate reports cross-validation errors for an invalid document`() {
        setup()
        val invalid = validDocument().copy(messageStore = MessageStoreConfig(retentionDays = 400, deduplicationRetentionDays = 30))

        val (statusCode, body) = request("POST", "/config/yaml/validate", codec.encode(invalid))

        assertEquals(200, statusCode)
        val result = json.decodeFromString<ValidationResultDto>(body)
        assertTrue(!result.valid)
        assertTrue(result.errors.any { it.field == "messageStore.deduplicationRetentionDays" })
    }

    @Test
    fun `POST config_yaml_validate with malformed YAML returns 400`() {
        setup()

        val (statusCode, _) = request("POST", "/config/yaml/validate", "not: [valid: yaml: at all")

        assertEquals(400, statusCode)
    }

    @Test
    fun `POST config_yaml_import stores a valid document and it becomes exportable`() {
        setup()
        val yamlText = codec.encode(validDocument())

        val (importStatus, _) = request("POST", "/config/yaml/import", yamlText)
        val (exportStatus, exportBody) = request("GET", "/config/yaml/export")

        assertEquals(200, importStatus)
        assertEquals(200, exportStatus)
        assertEquals(validDocument(), codec.decode(exportBody))
    }

    @Test
    fun `POST config_yaml_import rejects an invalid document without storing it`() {
        val configurationProvider = setup()
        val invalid = validDocument().copy(messageStore = MessageStoreConfig(retentionDays = 400, deduplicationRetentionDays = 30))

        val (statusCode, _) = request("POST", "/config/yaml/import", codec.encode(invalid))

        assertEquals(422, statusCode)
        assertEquals(null, configurationProvider.getValue(ConfigurationYamlEndpoints.FULL_CONFIGURATION_KEY))
    }

    @Test
    fun `GET config_yaml_export with nothing imported yet returns 404`() {
        setup()

        val (statusCode, _) = request("GET", "/config/yaml/export")

        assertEquals(404, statusCode)
    }

    @Test
    fun `config_yaml history and rollback operate on whole-document snapshots`() {
        setup()
        request("POST", "/config/yaml/import", codec.encode(validDocument()))
        val secondDocument = validDocument().copy(gateway = GatewayConfig(instanceId = "midomail-02", logLevel = "INFO"))
        request("POST", "/config/yaml/import", codec.encode(secondDocument))

        val (historyStatus, historyBody) = request("GET", "/config/yaml/history")
        val (rollbackStatus, _) = request("POST", "/config/yaml/rollback")
        val (_, exportBody) = request("GET", "/config/yaml/export")

        assertEquals(200, historyStatus)
        assertTrue(historyBody.contains("midomail-01"))
        assertEquals(200, rollbackStatus)
        assertEquals("midomail-01", codec.decode(exportBody).gateway.instanceId)
    }

    @Test
    fun `POST config_yaml_rollback with no history returns 409`() {
        setup()

        val (statusCode, _) = request("POST", "/config/yaml/rollback")

        assertEquals(409, statusCode)
    }
}
