package midomail.platform.jvm

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import midomail.adapter.rest.AdminHttpServer
import midomail.adapter.rest.YamlConfigurationCodec
import midomail.domain.configuration.AdapterConfigEntry
import midomail.domain.configuration.ConfigurationDocument
import midomail.domain.configuration.GatewayConfig
import midomail.domain.configuration.MessageStoreConfig
import midomail.domain.configuration.MonitoringConfig
import midomail.domain.configuration.NotificationsConfig
import midomail.domain.configuration.RoutingConfig
import midomail.domain.configuration.RoutingRuleConditionsConfig
import midomail.domain.configuration.RoutingRuleConfig
import midomail.domain.configuration.SchedulerConfig
import midomail.domain.configuration.SecurityConfig
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Weryfikacja end-to-end `:platform-jvm` (Iteracja 7.8, ADR-0036) — proces złożony przez
 * [buildGateway] z PRAWDZIWYMI adapterami: Email (przeciw lokalnemu GreenMail, ten sam duch co
 * Faza 2 — real components, nie mock) i WebSocket (przeciw lokalnemu [TestWebSocketServer], ten
 * sam serwer testowy co Iteracja 7.5).
 *
 * Scenariusz przekrojowy: e-mail przychodzący → GatewayEngine (Exactly Once + Routing, bez
 * modyfikacji) → wysłany dalej przez WebSocket — dowód, że Email + WebSocket + REST + CLI + UI
 * współdzielą TĘ SAMĄ, żywą instancję `Registry`/`GatewayEngine` w jednym procesie (domyka
 * odłożony z Fazy 5 test międzyadapterowy REST↔CLI, teraz na trzecim kanale).
 */
class PlatformJvmEndToEndTest {

    private lateinit var greenMail: GreenMail
    private lateinit var webSocketServer: TestWebSocketServer
    private lateinit var gateway: PlatformJvmGateway
    private lateinit var configPath: java.nio.file.Path
    private lateinit var secretsPath: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        greenMail = GreenMail(ServerSetupTest.SMTP_IMAP)
        greenMail.start()
        greenMail.setUser("recipient@example.com", "recipient@example.com", "test-password")

        webSocketServer = TestWebSocketServer()
        webSocketServer.start()

        configPath = Files.createTempFile("platform-jvm-e2e-config", ".properties").also { it.deleteIfExists() }
        secretsPath = Files.createTempFile("platform-jvm-e2e-secrets", ".properties").also { it.deleteIfExists() }

        FileSecretStore(secretsPath).apply {
            write("email-primary/username", "recipient@example.com")
            write("email-primary/password", "test-password")
            write("email-primary/smtp-host", "localhost")
            write("email-primary/smtp-port", ServerSetupTest.SMTP.port.toString())
            write("email-primary/smtp-ssl", "false")
            write("email-primary/smtp-starttls", "false")
            write("email-primary/imap-host", "localhost")
            write("email-primary/imap-port", ServerSetupTest.IMAP.port.toString())
            write("email-primary/imaps", "false")
            write("email-primary/poll-interval-millis", "200")
        }

        val document = ConfigurationDocument(
            version = "2.0",
            gateway = GatewayConfig("midomail-e2e", "INFO"),
            routing = RoutingConfig(
                rules = listOf(
                    RoutingRuleConfig(
                        ruleId = "email-to-websocket",
                        priority = 100,
                        conditions = RoutingRuleConditionsConfig(sourceChannel = "email"),
                        targetChannel = "websocket",
                        targetAdapter = "websocket-primary",
                        deliveryPolicy = "AT_LEAST_ONCE"
                    )
                )
            ),
            adapters = listOf(
                AdapterConfigEntry(
                    adapterId = "websocket-primary",
                    type = "websocket",
                    config = mapOf("url" to webSocketServer.url)
                )
            ),
            scheduler = SchedulerConfig(),
            security = SecurityConfig("file"),
            monitoring = MonitoringConfig(30),
            messageStore = MessageStoreConfig(30, 365),
            notifications = NotificationsConfig()
        )
        FileConfigurationProvider(configPath).setValue("gateway.fullConfiguration", YamlConfigurationCodec().encode(document))

        gateway = buildGateway(ProcessOptions(configPath = configPath.toString(), secretsPath = secretsPath.toString(), adminPort = 0, uiPort = 0))
    }

    @AfterTest
    fun tearDown() {
        gateway.stop()
        webSocketServer.stop()
        greenMail.stop()
        configPath.deleteIfExists()
        secretsPath.deleteIfExists()
    }

    private fun awaitCondition(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(condition(), "Warunek nie spełniony w limicie $timeoutMillis ms")
    }

    private fun restGet(path: String): Pair<Int, String> {
        val connection = URL("http://localhost:${gateway.adminServer.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, gateway.apiKey)
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to body
    }

    @Test
    fun `both real adapters (Email, WebSocket) are registered in the single shared Registry at startup`() {
        assertTrue(gateway.registeredAdapters.any { it.adapterId.value == "email-primary" })
        assertTrue(gateway.registeredAdapters.any { it.adapterId.value == "websocket-primary" })
    }

    @Test
    fun `GET adapters (REST) and the adapters CLI command report the same two adapters - single shared instance`() {
        val (status, body) = restGet("/adapters")
        val cliOutput = gateway.cliDispatcher.dispatch(arrayOf("adapters"))

        assertTrue(status == 200)
        assertTrue(body.contains("email-primary") && body.contains("websocket-primary"))
        assertTrue(cliOutput.contains("email-primary") && cliOutput.contains("websocket-primary"))
    }

    @Test
    fun `UI static file server serves index html in the same process`() {
        val connection = URL("http://localhost:${gateway.uiServer.port()}/").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        assertTrue(connection.responseCode == 200)
        assertTrue(connection.inputStream.readBytes().toString(Charsets.UTF_8).contains("midoMAIL"))
    }

    @Test
    fun `an email received via real SMTP-IMAP is routed and forwarded out through the real WebSocket adapter`() {
        GreenMailUtil.sendTextEmail(
            "recipient@example.com",
            "sender@example.com",
            "Temat end-to-end",
            "Tresc end-to-end platform-jvm",
            ServerSetupTest.SMTP
        )

        awaitCondition { webSocketServer.receivedMessages.any { it.contains("Tresc end-to-end platform-jvm") } }
    }

    @Test
    fun `the forwarded message is visible through GET messages (REST) - proves Gateway Engine and MessageStore are the single live instance behind Email, WebSocket, and REST`() {
        GreenMailUtil.sendTextEmail(
            "recipient@example.com",
            "sender@example.com",
            "Temat widoczny przez REST",
            "Tresc widoczna przez REST",
            ServerSetupTest.SMTP
        )

        awaitCondition {
            val (status, body) = restGet("/messages?channelType=email")
            status == 200 && body.contains("Tresc widoczna przez REST")
        }
    }
}
