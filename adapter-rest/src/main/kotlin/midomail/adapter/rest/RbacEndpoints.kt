package midomail.adapter.rest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.AccountDto
import midomail.adapter.rest.dto.LoginRequestDto
import midomail.adapter.rest.dto.LoginResultDto
import midomail.adapter.rest.dto.RoleDto
import midomail.adapter.rest.dto.toDomain
import midomail.adapter.rest.dto.toDto
import midomail.domain.rbac.Account
import midomail.domain.rbac.AccountAuthenticator
import midomail.domain.rbac.AccountId
import midomail.domain.rbac.AccountStatus
import midomail.domain.rbac.AccountStore
import midomail.domain.rbac.AuthenticationResult
import midomail.domain.rbac.RoleId
import midomail.domain.rbac.RoleStore

/**
 * Endpointy RBAC (SPEC-0025, ADR-0030, 70-Uzytkownicy-i-uprawnienia.md) — konta i role.
 *
 * **Zakres tej iteracji:** wystawia operacje domenowe (`AccountStore`/`RoleStore`/
 * `AccountAuthenticator`) przez Admin API, chronione tym samym statycznym kluczem API co
 * wszystkie inne trasy (`AdminHttpServer`'s `X-API-Key`, ADR-0019) — model jednego administratora
 * z Androida NADAL jest bramą wejścia do całego API. `POST /accounts/login` weryfikuje
 * poświadczenia KONTA (osobne od klucza API), ale nie ustanawia sesji/tokenu — pełne egzekwowanie
 * autoryzacji PER OPERACJA na podstawie roli zalogowanego konta wymagałoby głębszej zmiany w
 * `AdminHttpServer` (dyspozycja świadoma uprawnień), świadomie poza zakresem Fazy 6 (model
 * weryfikowany harnessem, nie rzeczywistym wdrożeniem — patrz decyzja 2, Kontekst planu).
 */
class RbacEndpoints(
    private val accountStore: AccountStore,
    private val roleStore: RoleStore,
    private val authenticator: AccountAuthenticator
) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/roles") { exchange ->
            AdminHttpServer.respond(exchange, 200, json.encodeToString(roleStore.all().map { it.toDto() }))
        }

        server.route("POST", "/roles") { exchange ->
            val dto = readBody<RoleDto>(exchange) ?: run {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowe ciało żądania")
                return@route
            }
            roleStore.save(dto.toDomain())
            AdminHttpServer.respond(exchange, 200, "OK")
        }

        server.route("GET", "/accounts") { exchange ->
            AdminHttpServer.respond(exchange, 200, json.encodeToString(accountStore.all().map { it.toDto() }))
        }

        server.route("POST", "/accounts") { exchange ->
            val body = readBody<Map<String, String>>(exchange) ?: run {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowe ciało żądania")
                return@route
            }
            val username = body["username"]
            val password = body["password"]
            val roleId = body["roleId"]
            if (username == null || password == null || roleId == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagane pola: username, password, roleId")
                return@route
            }
            try {
                accountStore.create(
                    Account(AccountId(java.util.UUID.randomUUID().toString()), username, RoleId(roleId)),
                    passwordHash = AccountAuthenticator.hash(password)
                )
                AdminHttpServer.respond(exchange, 200, "OK")
            } catch (exception: IllegalArgumentException) {
                AdminHttpServer.respond(exchange, 409, exception.message ?: "Konflikt")
            }
        }

        server.route("POST", "/accounts/status") { exchange ->
            val id = queryParam(exchange, "id")
            val status = queryParam(exchange, "status")
            if (id == null || status == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagane parametry zapytania: id, status")
                return@route
            }
            accountStore.updateStatus(AccountId(id), AccountStatus.valueOf(status))
            AdminHttpServer.respond(exchange, 200, "OK")
        }

        server.route("POST", "/accounts/role") { exchange ->
            val id = queryParam(exchange, "id")
            val roleId = queryParam(exchange, "roleId")
            if (id == null || roleId == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagane parametry zapytania: id, roleId")
                return@route
            }
            accountStore.updateRole(AccountId(id), RoleId(roleId))
            AdminHttpServer.respond(exchange, 200, "OK")
        }

        server.route("POST", "/accounts/reset-password") { exchange ->
            val id = queryParam(exchange, "id")
            if (id == null) {
                AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: id")
                return@route
            }
            val newPassword = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            accountStore.resetPassword(AccountId(id), AccountAuthenticator.hash(newPassword))
            AdminHttpServer.respond(exchange, 200, "OK")
        }

        server.route("POST", "/accounts/login") { exchange ->
            val request = readBody<LoginRequestDto>(exchange) ?: run {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowe ciało żądania")
                return@route
            }
            val result = authenticator.authenticate(request.username, request.password)
            val dto = when (result) {
                is AuthenticationResult.Success -> LoginResultDto("SUCCESS", result.account.toDto())
                AuthenticationResult.InvalidCredentials -> LoginResultDto("INVALID_CREDENTIALS")
                AuthenticationResult.AccountBlocked -> LoginResultDto("ACCOUNT_BLOCKED")
            }
            val statusCode = if (result is AuthenticationResult.Success) 200 else 401
            AdminHttpServer.respond(exchange, statusCode, json.encodeToString(dto))
        }
    }

    private inline fun <reified T> readBody(exchange: com.sun.net.httpserver.HttpExchange): T? = try {
        json.decodeFromString<T>(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    private fun queryParam(exchange: com.sun.net.httpserver.HttpExchange, name: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
    }
}
