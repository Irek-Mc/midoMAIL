package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.RoutingConditionsDto
import midomail.adapter.rest.dto.RoutingDecisionDto
import midomail.adapter.rest.dto.RoutingRuleDto
import midomail.adapter.rest.dto.SimulateRoutingRequestDto
import midomail.adapter.rest.dto.toDomain
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.administration.RoutingRuleAdministration
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza endpointy administracji routingu (SPEC-0024, §4) na prawdziwym serwerze/kliencie.
 */
class RoutingEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(administration: RoutingRuleAdministration = RoutingRuleAdministration()): RoutingRuleAdministration {
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        RoutingEndpoints(administration).registerRoutes(server)
        server.start()
        return administration
    }

    private fun postJson(path: String, body: String): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to responseBody
    }

    private fun delete(path: String): Int {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        return connection.responseCode
    }

    private fun sampleRuleDto(ruleId: String = "rule-1", priority: Int = 100): RoutingRuleDto = RoutingRuleDto(
        ruleId = ruleId,
        priority = priority,
        enabled = true,
        conditions = RoutingConditionsDto(sourceChannel = "sms"),
        targetChannel = "email",
        targetAdapter = "email-primary",
        deliveryPolicy = "AT_LEAST_ONCE",
        version = "1"
    )

    @Test
    fun `POST routing_rules adds a rule visible via list`() {
        val administration = setup()

        val (statusCode, _) = postJson("/routing/rules", json.encodeToString(sampleRuleDto()))

        assertEquals(200, statusCode)
        assertEquals(1, administration.list().size)
    }

    @Test
    fun `adding a rule with a duplicate ruleId returns 409`() {
        val administration = setup()
        administration.add(sampleRuleDto().toDomain())

        val (statusCode, _) = postJson("/routing/rules", json.encodeToString(sampleRuleDto()))

        assertEquals(409, statusCode)
    }

    @Test
    fun `POST routing_rules_update changes the rule and increments its version`() {
        val administration = setup()
        administration.add(sampleRuleDto().toDomain())

        val (statusCode, body) = postJson(
            "/routing/rules/update?ruleId=rule-1",
            json.encodeToString(sampleRuleDto(priority = 200))
        )

        assertEquals(200, statusCode)
        val updated = json.decodeFromString<RoutingRuleDto>(body)
        assertEquals(200, updated.priority)
        assertEquals("2", updated.version)
    }

    @Test
    fun `updating an unknown rule returns 404`() {
        setup()

        val (statusCode, _) = postJson("/routing/rules/update?ruleId=unknown", json.encodeToString(sampleRuleDto()))

        assertEquals(404, statusCode)
    }

    @Test
    fun `DELETE routing_rules removes the rule`() {
        val administration = setup()
        administration.add(sampleRuleDto().toDomain())

        val statusCode = delete("/routing/rules?ruleId=rule-1")

        assertEquals(200, statusCode)
        assertTrue(administration.list().isEmpty())
    }

    @Test
    fun `POST routing_simulate matches an existing rule without side effects`() {
        val administration = setup()
        administration.add(sampleRuleDto().toDomain())

        val (statusCode, body) = postJson(
            "/routing/simulate",
            json.encodeToString(SimulateRoutingRequestDto(sourceChannel = "sms", destinationChannel = "email"))
        )

        assertEquals(200, statusCode)
        val decision = json.decodeFromString<RoutingDecisionDto>(body)
        assertTrue(decision.matched)
        assertEquals("email-primary", decision.targetAdapter)
        assertEquals("NORMAL", decision.messagePriorityBefore)
        assertEquals(1, administration.list().size, "Symulacja nie powinna modyfikować zestawu reguł")
    }

    @Test
    fun `POST routing_simulate with no matching rule returns matched false`() {
        setup()

        val (statusCode, body) = postJson(
            "/routing/simulate",
            json.encodeToString(SimulateRoutingRequestDto(sourceChannel = "sms", destinationChannel = "email"))
        )

        assertEquals(200, statusCode)
        val decision = json.decodeFromString<RoutingDecisionDto>(body)
        assertEquals(false, decision.matched)
    }

    @Test
    fun `POST routing_simulate reports messagePriorityAfter overridden by the rule's SetPriority`() {
        val administration = setup()
        administration.add(sampleRuleDto().toDomain().copy(setPriority = midomail.domain.message.MessagePriority.HIGH))

        val (_, body) = postJson(
            "/routing/simulate",
            json.encodeToString(SimulateRoutingRequestDto(sourceChannel = "sms", destinationChannel = "email", messagePriority = "LOW"))
        )

        val decision = json.decodeFromString<RoutingDecisionDto>(body)
        assertEquals("LOW", decision.messagePriorityBefore)
        assertEquals("HIGH", decision.messagePriorityAfter)
    }

    @Test
    fun `GET routing_rules_history returns changes in chronological order`() {
        val administration = setup()
        postJson("/routing/rules", json.encodeToString(sampleRuleDto()))
        postJson("/routing/rules/update?ruleId=rule-1", json.encodeToString(sampleRuleDto(priority = 200)))
        delete("/routing/rules?ruleId=rule-1")

        val (statusCode, body) = get("/routing/rules/history")

        assertEquals(200, statusCode)
        val history = json.decodeFromString<List<midomail.adapter.rest.dto.RoutingRuleChangeDto>>(body)
        assertEquals(listOf("ADDED", "UPDATED", "REMOVED"), history.map { it.changeType })
    }

    private fun get(path: String): Pair<Int, String> {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        return responseCode to body
    }
}
