package midomail.domain.rbac

@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId nie może być pusty" }
    }
}

enum class AccountStatus { ACTIVE, BLOCKED }

/**
 * Konto administracyjne (ADR-0030-RBAC-Model.md, 70-Uzytkownicy-i-uprawnienia.md §4). Hasło NIE
 * jest polem tego typu — przechowywane wyłącznie jako hash przez [AccountStore], nigdy w jawnej
 * postaci w modelu domenowym.
 */
data class Account(
    val accountId: AccountId,
    val username: String,
    val roleId: RoleId,
    val status: AccountStatus = AccountStatus.ACTIVE
)

/** Port repozytorium kont (ADR-0030). */
interface AccountStore {
    fun findByUsername(username: String): Account?
    fun findById(accountId: AccountId): Account?
    fun all(): List<Account>
    fun create(account: Account, passwordHash: String)
    fun updateStatus(accountId: AccountId, status: AccountStatus)
    fun updateRole(accountId: AccountId, roleId: RoleId)
    fun resetPassword(accountId: AccountId, newPasswordHash: String)

    /** Wyłącznie dla [AccountAuthenticator] — hash, nigdy hasło w jawnej postaci. */
    fun passwordHashOf(accountId: AccountId): String?
}
