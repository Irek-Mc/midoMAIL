package midomail.domain.rbac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InMemoryAccountStoreTest {

    private fun account(username: String = "alice", accountId: String = "account-1"): Account = Account(
        accountId = AccountId(accountId),
        username = username,
        roleId = RoleId("administrator")
    )

    @Test
    fun `create then findByUsername and findById return the created account`() {
        val store = InMemoryAccountStore()

        store.create(account(), passwordHash = "hash-1")

        assertEquals(AccountId("account-1"), store.findByUsername("alice")?.accountId)
        assertEquals("alice", store.findById(AccountId("account-1"))?.username)
    }

    @Test
    fun `create with an already-used username fails`() {
        val store = InMemoryAccountStore()
        store.create(account(username = "alice", accountId = "account-1"), "hash-1")

        assertFailsWith<IllegalArgumentException> {
            store.create(account(username = "alice", accountId = "account-2"), "hash-2")
        }
    }

    @Test
    fun `updateStatus changes the account's status`() {
        val store = InMemoryAccountStore()
        store.create(account(), "hash-1")

        store.updateStatus(AccountId("account-1"), AccountStatus.BLOCKED)

        assertEquals(AccountStatus.BLOCKED, store.findById(AccountId("account-1"))?.status)
    }

    @Test
    fun `updateRole changes the account's role`() {
        val store = InMemoryAccountStore()
        store.create(account(), "hash-1")

        store.updateRole(AccountId("account-1"), RoleId("audytor"))

        assertEquals(RoleId("audytor"), store.findById(AccountId("account-1"))?.roleId)
    }

    @Test
    fun `resetPassword changes the stored password hash`() {
        val store = InMemoryAccountStore()
        store.create(account(), "hash-1")

        store.resetPassword(AccountId("account-1"), "hash-2")

        assertEquals("hash-2", store.passwordHashOf(AccountId("account-1")))
    }

    @Test
    fun `resetPassword on an unknown account has no effect`() {
        val store = InMemoryAccountStore()

        store.resetPassword(AccountId("unknown"), "hash")

        assertNull(store.passwordHashOf(AccountId("unknown")))
    }

    @Test
    fun `passwordHashOf returns null for an unknown account`() {
        val store = InMemoryAccountStore()

        assertNull(store.passwordHashOf(AccountId("unknown")))
    }
}
