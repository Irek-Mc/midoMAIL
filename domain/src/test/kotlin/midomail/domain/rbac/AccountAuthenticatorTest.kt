package midomail.domain.rbac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Potwierdza `AccountAuthenticator` (ADR-0030) oraz pełny scenariusz z planu Fazy 6: tworzenie
 * konta, przypisanie roli, sprawdzenie uprawnienia do ekranu.
 */
class AccountAuthenticatorTest {

    @Test
    fun `authenticate succeeds with the correct password`() {
        val accountStore = InMemoryAccountStore()
        accountStore.create(
            Account(AccountId("account-1"), "alice", RoleId("administrator")),
            passwordHash = AccountAuthenticator.hash("correct-password")
        )
        val authenticator = AccountAuthenticator(accountStore)

        val result = authenticator.authenticate("alice", "correct-password")

        assertIs<AuthenticationResult.Success>(result)
        assertEquals("alice", result.account.username)
    }

    @Test
    fun `authenticate fails with an incorrect password`() {
        val accountStore = InMemoryAccountStore()
        accountStore.create(
            Account(AccountId("account-1"), "alice", RoleId("administrator")),
            passwordHash = AccountAuthenticator.hash("correct-password")
        )
        val authenticator = AccountAuthenticator(accountStore)

        val result = authenticator.authenticate("alice", "wrong-password")

        assertEquals(AuthenticationResult.InvalidCredentials, result)
    }

    @Test
    fun `authenticate fails for an unknown username`() {
        val authenticator = AccountAuthenticator(InMemoryAccountStore())

        val result = authenticator.authenticate("unknown", "any-password")

        assertEquals(AuthenticationResult.InvalidCredentials, result)
    }

    @Test
    fun `authenticate rejects a BLOCKED account even with the correct password`() {
        val accountStore = InMemoryAccountStore()
        accountStore.create(
            Account(AccountId("account-1"), "alice", RoleId("administrator")),
            passwordHash = AccountAuthenticator.hash("correct-password")
        )
        accountStore.updateStatus(AccountId("account-1"), AccountStatus.BLOCKED)
        val authenticator = AccountAuthenticator(accountStore)

        val result = authenticator.authenticate("alice", "correct-password")

        assertEquals(AuthenticationResult.AccountBlocked, result)
    }

    @Test
    fun `full scenario - create account, assign role, verify screen permission`() {
        val roleStore = InMemoryRoleStore()
        val accountStore = InMemoryAccountStore()
        roleStore.save(Role(RoleId("audytor"), "Audytor", setOf(Permission(AdministeredArea.ROUTING, AccessLevel.READ))))
        accountStore.create(
            Account(AccountId("account-1"), "bob", RoleId("audytor")),
            passwordHash = AccountAuthenticator.hash("bob-password")
        )
        val authenticator = AccountAuthenticator(accountStore)

        val authResult = authenticator.authenticate("bob", "bob-password")
        assertIs<AuthenticationResult.Success>(authResult)

        val role = roleStore.findById(authResult.account.roleId)!!
        assertTrue(role.grants(AdministeredArea.ROUTING, AccessLevel.READ))
        assertTrue(!role.grants(AdministeredArea.ROUTING, AccessLevel.WRITE), "Audytor ma wyłącznie odczyt, nie zapis")
        assertTrue(!role.grants(AdministeredArea.USERS, AccessLevel.READ), "Audytor nie ma dostępu do zarządzania kontami")
    }
}
