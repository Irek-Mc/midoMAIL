package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AccountDto
import midomail.adapter.rest.dto.LoginRequestDto
import midomail.adapter.rest.dto.LoginResultDto
import midomail.adapter.rest.dto.PermissionDto
import midomail.adapter.rest.dto.RoleDto
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.rbac.AccountAuthenticator
import midomail.domain.rbac.InMemoryAccountStore
import midomail.domain.rbac.InMemoryRoleStore
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza `RbacEndpoints` (SPEC-0025, ADR-0030) na prawdziwym serwerze/kliencie.
 */
class RbacEndpointsTest {

    private lateinit var server: AdminHttpServer
    private val json = Json { ignoreUnknownKeys = true }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(): Pair<InMemoryAccountStore, InMemoryRoleStore> {
        val accountStore = InMemoryAccountStore()
        val roleStore = InMemoryRoleStore()
        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        RbacEndpoints(accountStore, roleStore, AccountAuthenticator(accountStore)).registerRoutes(server)
        server.start()
        return accountStore to roleStore
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
    fun `POST roles then GET roles round-trips a role with its permissions`() {
        setup()
        val role = RoleDto("audytor", "Audytor", listOf(PermissionDto("ROUTING", "READ")))

        request("POST", "/roles", json.encodeToString(role))
        val (statusCode, body) = request("GET", "/roles")

        assertEquals(200, statusCode)
        val roles = json.decodeFromString<List<RoleDto>>(body)
        assertEquals(role, roles.single())
    }

    @Test
    fun `POST accounts creates an account visible via GET accounts`() {
        setup()

        val (createStatus, _) = request("POST", "/accounts", """{"username":"alice","password":"secret","roleId":"administrator"}""")
        val (statusCode, body) = request("GET", "/accounts")

        assertEquals(200, createStatus)
        assertEquals(200, statusCode)
        val accounts = json.decodeFromString<List<AccountDto>>(body)
        assertEquals("alice", accounts.single().username)
    }

    @Test
    fun `POST accounts_login succeeds with the correct password`() {
        val (accountStore, _) = setup()
        request("POST", "/accounts", """{"username":"alice","password":"secret","roleId":"administrator"}""")

        val (statusCode, body) = request("POST", "/accounts/login", json.encodeToString(LoginRequestDto("alice", "secret")))

        assertEquals(200, statusCode)
        val result = json.decodeFromString<LoginResultDto>(body)
        assertEquals("SUCCESS", result.outcome)
        assertEquals("alice", result.account?.username)
    }

    @Test
    fun `POST accounts_login fails with the wrong password`() {
        setup()
        request("POST", "/accounts", """{"username":"alice","password":"secret","roleId":"administrator"}""")

        val (statusCode, body) = request("POST", "/accounts/login", json.encodeToString(LoginRequestDto("alice", "wrong")))

        assertEquals(401, statusCode)
        assertEquals("INVALID_CREDENTIALS", json.decodeFromString<LoginResultDto>(body).outcome)
    }

    @Test
    fun `POST accounts_status blocks an account, then login reports ACCOUNT_BLOCKED`() {
        setup()
        val (_, createBody) = request("POST", "/accounts", """{"username":"alice","password":"secret","roleId":"administrator"}""")
        val accountId = json.decodeFromString<List<AccountDto>>(request("GET", "/accounts").second).single().accountId

        request("POST", "/accounts/status?id=$accountId&status=BLOCKED")
        val (statusCode, body) = request("POST", "/accounts/login", json.encodeToString(LoginRequestDto("alice", "secret")))

        assertEquals(401, statusCode)
        assertEquals("ACCOUNT_BLOCKED", json.decodeFromString<LoginResultDto>(body).outcome)
    }

    @Test
    fun `POST accounts_reset-password changes the password used for login`() {
        setup()
        request("POST", "/accounts", """{"username":"alice","password":"secret","roleId":"administrator"}""")
        val accountId = json.decodeFromString<List<AccountDto>>(request("GET", "/accounts").second).single().accountId

        request("POST", "/accounts/reset-password?id=$accountId", "new-secret")
        val (statusCode, body) = request("POST", "/accounts/login", json.encodeToString(LoginRequestDto("alice", "new-secret")))

        assertEquals(200, statusCode)
        assertEquals("SUCCESS", json.decodeFromString<LoginResultDto>(body).outcome)
    }

    @Test
    fun `POST accounts with an already-used username returns 409`() {
        setup()
        request("POST", "/accounts", """{"username":"alice","password":"secret","roleId":"administrator"}""")

        val (statusCode, _) = request("POST", "/accounts", """{"username":"alice","password":"other","roleId":"administrator"}""")

        assertEquals(409, statusCode)
    }
}
