package midomail.adapter.cli

import midomail.domain.rbac.AccessLevel
import midomail.domain.rbac.AccountAuthenticator
import midomail.domain.rbac.AdministeredArea
import midomail.domain.rbac.InMemoryAccountStore
import midomail.domain.rbac.InMemoryRoleStore
import midomail.domain.rbac.Permission
import midomail.domain.rbac.Role
import midomail.domain.rbac.RoleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RbacCommandsTest {

    private fun setup(): Triple<CliDispatcher, InMemoryAccountStore, InMemoryRoleStore> {
        val accountStore = InMemoryAccountStore()
        val roleStore = InMemoryRoleStore()
        val dispatcher = CliDispatcher(RbacCommands(accountStore, roleStore, AccountAuthenticator(accountStore)).commands())
        return Triple(dispatcher, accountStore, roleStore)
    }

    @Test
    fun `roles command lists saved roles`() {
        val (dispatcher, _, roleStore) = setup()
        roleStore.save(Role(RoleId("audytor"), "Audytor", setOf(Permission(AdministeredArea.ROUTING, AccessLevel.READ))))

        val output = dispatcher.dispatch(arrayOf("roles"))

        assertTrue(output.contains("audytor"))
        assertTrue(output.contains("Audytor"))
    }

    @Test
    fun `account-create then accounts lists the new account`() {
        val (dispatcher, _, _) = setup()

        dispatcher.dispatch(arrayOf("account-create", "alice", "secret", "administrator"))
        val output = dispatcher.dispatch(arrayOf("accounts"))

        assertTrue(output.contains("alice"))
        assertTrue(output.contains("role=administrator"))
    }

    @Test
    fun `login succeeds with the correct password`() {
        val (dispatcher, _, _) = setup()
        dispatcher.dispatch(arrayOf("account-create", "alice", "secret", "administrator"))

        val output = dispatcher.dispatch(arrayOf("login", "alice", "secret"))

        assertTrue(output.startsWith("OK: zalogowano jako alice"))
    }

    @Test
    fun `login fails with the wrong password`() {
        val (dispatcher, _, _) = setup()
        dispatcher.dispatch(arrayOf("account-create", "alice", "secret", "administrator"))

        val output = dispatcher.dispatch(arrayOf("login", "alice", "wrong"))

        assertEquals("Nieprawidłowe poświadczenia", output)
    }

    @Test
    fun `account-status blocks an account, then login reports blocked`() {
        val (dispatcher, accountStore, _) = setup()
        dispatcher.dispatch(arrayOf("account-create", "alice", "secret", "administrator"))
        val accountId = accountStore.findByUsername("alice")!!.accountId.value

        dispatcher.dispatch(arrayOf("account-status", accountId, "BLOCKED"))
        val output = dispatcher.dispatch(arrayOf("login", "alice", "secret"))

        assertEquals("Konto zablokowane", output)
    }

    @Test
    fun `account-reset-password changes the password used for login`() {
        val (dispatcher, accountStore, _) = setup()
        dispatcher.dispatch(arrayOf("account-create", "alice", "secret", "administrator"))
        val accountId = accountStore.findByUsername("alice")!!.accountId.value

        dispatcher.dispatch(arrayOf("account-reset-password", accountId, "new-secret"))
        val output = dispatcher.dispatch(arrayOf("login", "alice", "new-secret"))

        assertTrue(output.startsWith("OK"))
    }
}
