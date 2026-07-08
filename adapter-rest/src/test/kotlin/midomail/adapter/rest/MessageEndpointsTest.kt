package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.MessageDto
import midomail.adapter.rest.dto.MessagePageDto
import midomail.adapter.rest.dto.ReprocessResultDto
import midomail.domain.adapter.AdapterId
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.administration.MessageReprocessingAdministration
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.gateway.GatewayEngine
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.port.GatewayOutbound
import midomail.domain.port.memory.InMemoryEventPublisher
import midomail.domain.port.memory.InMemoryMessageStore
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `MessageEndpoints` (SPEC-0025, 62-Komunikaty.md) na prawdziwym serwerze/kliencie i
 * prawdziwym `GatewayEngine` (dla reprocess).
 */
class MessageEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }

    private class NoopGatewayOutbound : GatewayOutbound {
        override fun send(message: GatewayMessage) {}
    }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun sampleMessage(externalReference: String = "<ext-1@localhost>"): GatewayMessage = GatewayMessage(
        identity = Identity(
            messageId = MessageId("message-1"),
            correlationId = CorrelationId("correlation-1"),
            schemaVersion = SchemaVersion("2.0"),
            externalReference = ExternalReference(externalReference)
        ),
        source = Channel(type = ChannelType("gsm")),
        destination = Channel(type = ChannelType("email")),
        payload = Payload(content = "Treść testowa")
    )

    private fun setup(): Pair<InMemoryMessageStore, GatewayEngine> {
        val messageStore = InMemoryMessageStore()
        val engine = GatewayEngine(
            exactlyOnceEngine = ExactlyOnceEngine(messageStore),
            routingEngine = RoutingEngine(
                listOf(
                    RoutingRule(
                        ruleId = RuleId("rule-1"), priority = 100, targetChannel = ChannelType("email"),
                        targetAdapter = AdapterId("email-primary"), deliveryPolicy = DeliveryPolicy("AT_LEAST_ONCE"),
                        version = RuleVersion("1")
                    )
                )
            ),
            eventPublisher = InMemoryEventPublisher(),
            gatewayOutbound = NoopGatewayOutbound()
        )
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        MessageEndpoints(messageStore, MessageReprocessingAdministration(messageStore, engine)).registerRoutes(server)
        server.start()
        return messageStore to engine
    }

    private fun get(path: String, method: String = "GET"): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        if (method != "GET") {
            connection.doOutput = true
            connection.outputStream.close()
        }
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to body
    }

    @Test
    fun `GET messages includes createdAt and updatedAt from MessageStore metadata`() {
        val (_, engine) = setup()
        engine.receive(sampleMessage())

        val (statusCode, body) = get("/messages?channelType=gsm")

        assertEquals(200, statusCode)
        val page = json.decodeFromString<MessagePageDto>(body)
        assertTrue(page.items.single().createdAt != null)
        assertTrue(page.items.single().updatedAt != null)
    }

    @Test
    fun `GET messages lists messages matching the filter`() {
        val (messageStore, engine) = setup()
        engine.receive(sampleMessage())

        val (statusCode, body) = get("/messages?channelType=gsm")

        assertEquals(200, statusCode)
        val page = json.decodeFromString<MessagePageDto>(body)
        assertEquals(1, page.items.size)
        assertEquals("correlation-1", page.items.single().correlationId)
    }

    @Test
    fun `GET messages_find by externalReference returns the message`() {
        val (_, engine) = setup()
        engine.receive(sampleMessage())

        val (statusCode, body) = get("/messages/find?externalReference=%3Cext-1%40localhost%3E")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<MessageDto>(body)
        assertEquals("correlation-1", dto.correlationId)
    }

    @Test
    fun `GET messages_find without any lookup parameter returns 400`() {
        setup()

        val (statusCode, _) = get("/messages/find")

        assertEquals(400, statusCode)
    }

    @Test
    fun `GET messages_find for an unknown message returns 404`() {
        setup()

        val (statusCode, _) = get("/messages/find?messageId=unknown")

        assertEquals(404, statusCode)
    }

    @Test
    fun `POST messages_reprocess re-submits the message and returns the new MessageId`() {
        val (_, engine) = setup()
        engine.receive(sampleMessage())

        val (statusCode, body) = get("/messages/reprocess?externalReference=%3Cext-1%40localhost%3E", method = "POST")

        assertEquals(200, statusCode)
        val dto = json.decodeFromString<ReprocessResultDto>(body)
        assertEquals("REPROCESSED", dto.outcome)
        assertEquals("message-1" != dto.newMessageId, true)
    }

    @Test
    fun `POST messages_reprocess for an unknown ExternalReference returns 404`() {
        setup()

        val (statusCode, body) = get("/messages/reprocess?externalReference=unknown", method = "POST")

        assertEquals(404, statusCode)
        assertEquals("NOT_FOUND", json.decodeFromString<ReprocessResultDto>(body).outcome)
    }
}
