package midomail.domain.rbac

import java.security.MessageDigest

sealed class AuthenticationResult {
    data class Success(val account: Account) : AuthenticationResult()
    data object InvalidCredentials : AuthenticationResult()
    data object AccountBlocked : AuthenticationResult()
}

/**
 * Uwierzytelnianie kontem (ADR-0030-RBAC-Model.md) — odrębne od `SecretStoreAdminAuthenticator`
 * (Faza 5, statyczny klucz API dla Androida); oba mechanizmy współistnieją w Adminie REST
 * (rozstrzygane w Iteracji 6.15).
 *
 * **Hashowanie SHA-256 bez soli — świadome uproszczenie tej fazy** (ADR-0030, §Decyzja). Ten
 * model RBAC istnieje w Fazie 6 wyłącznie do zweryfikowania harnessem, nie do produkcyjnego
 * wystawienia — prawdziwe hashowanie (bcrypt/Argon2/PBKDF2 z solą) odkładane do Fazy 7.
 */
class AccountAuthenticator(private val accountStore: AccountStore) {

    fun authenticate(username: String, password: String): AuthenticationResult {
        val account = accountStore.findByUsername(username) ?: return AuthenticationResult.InvalidCredentials
        if (account.status == AccountStatus.BLOCKED) return AuthenticationResult.AccountBlocked

        val storedHash = accountStore.passwordHashOf(account.accountId) ?: return AuthenticationResult.InvalidCredentials
        val providedHash = hash(password)
        return if (MessageDigest.isEqual(storedHash.toByteArray(Charsets.UTF_8), providedHash.toByteArray(Charsets.UTF_8))) {
            AuthenticationResult.Success(account)
        } else {
            AuthenticationResult.InvalidCredentials
        }
    }

    companion object {
        fun hash(password: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(password.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
