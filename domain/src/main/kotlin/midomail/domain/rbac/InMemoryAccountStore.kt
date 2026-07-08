package midomail.domain.rbac

import java.util.concurrent.ConcurrentHashMap

/** Implementacja referencyjna [AccountStore] w pamięci (ADR-0030) — wzorem `InMemoryConfigurationProvider`. */
class InMemoryAccountStore : AccountStore {

    private val accountsById = ConcurrentHashMap<AccountId, Account>()
    private val passwordHashesById = ConcurrentHashMap<AccountId, String>()

    override fun findByUsername(username: String): Account? =
        accountsById.values.firstOrNull { it.username == username }

    override fun findById(accountId: AccountId): Account? = accountsById[accountId]

    override fun all(): List<Account> = accountsById.values.toList()

    override fun create(account: Account, passwordHash: String) {
        require(findByUsername(account.username) == null) { "Konto o nazwie ${account.username} już istnieje" }
        accountsById[account.accountId] = account
        passwordHashesById[account.accountId] = passwordHash
    }

    override fun updateStatus(accountId: AccountId, status: AccountStatus) {
        val current = accountsById[accountId] ?: return
        accountsById[accountId] = current.copy(status = status)
    }

    override fun updateRole(accountId: AccountId, roleId: RoleId) {
        val current = accountsById[accountId] ?: return
        accountsById[accountId] = current.copy(roleId = roleId)
    }

    override fun resetPassword(accountId: AccountId, newPasswordHash: String) {
        if (accountsById.containsKey(accountId)) {
            passwordHashesById[accountId] = newPasswordHash
        }
    }

    override fun passwordHashOf(accountId: AccountId): String? = passwordHashesById[accountId]
}
