package midomail.adapter.cli

import midomail.domain.rbac.Account
import midomail.domain.rbac.AccountAuthenticator
import midomail.domain.rbac.AccountId
import midomail.domain.rbac.AccountStatus
import midomail.domain.rbac.AccountStore
import midomail.domain.rbac.AuthenticationResult
import midomail.domain.rbac.RoleId
import midomail.domain.rbac.RoleStore
import java.util.UUID

/** Komendy CLI RBAC (SPEC-0025, ADR-0030) — lustro `RbacEndpoints`, zwykły tekst (nie JSON). */
class RbacCommands(
    private val accountStore: AccountStore,
    private val roleStore: RoleStore,
    private val authenticator: AccountAuthenticator
) {
    fun commands(): List<CliCommand> = listOf(
        RolesCommand(), AccountsCommand(), AccountCreateCommand(), AccountStatusCommand(),
        AccountResetPasswordCommand(), LoginCommand()
    )

    private inner class RolesCommand : CliCommand {
        override val name = "roles"
        override fun execute(args: List<String>): String {
            val roles = roleStore.all()
            if (roles.isEmpty()) return "Brak ról"
            return roles.joinToString("\n") { "${it.roleId.value}\t${it.name}\tpermissions=${it.permissions.size}" }
        }
    }

    private inner class AccountsCommand : CliCommand {
        override val name = "accounts"
        override fun execute(args: List<String>): String {
            val accounts = accountStore.all()
            if (accounts.isEmpty()) return "Brak kont"
            return accounts.joinToString("\n") { "${it.accountId.value}\t${it.username}\trole=${it.roleId.value}\tstatus=${it.status}" }
        }
    }

    private inner class AccountCreateCommand : CliCommand {
        override val name = "account-create"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            if (args.size < 3) return "Użycie: account-create <username> <password> <roleId>"
            return try {
                accountStore.create(
                    Account(AccountId(UUID.randomUUID().toString()), args[0], RoleId(args[2])),
                    passwordHash = AccountAuthenticator.hash(args[1])
                )
                "OK"
            } catch (exception: IllegalArgumentException) {
                exception.message ?: "Konflikt"
            }
        }
    }

    private inner class AccountStatusCommand : CliCommand {
        override val name = "account-status"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: account-status <accountId> <ACTIVE|BLOCKED>"
            accountStore.updateStatus(AccountId(args[0]), AccountStatus.valueOf(args[1]))
            return "OK"
        }
    }

    private inner class AccountResetPasswordCommand : CliCommand {
        override val name = "account-reset-password"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: account-reset-password <accountId> <nowe hasło>"
            accountStore.resetPassword(AccountId(args[0]), AccountAuthenticator.hash(args[1]))
            return "OK"
        }
    }

    private inner class LoginCommand : CliCommand {
        override val name = "login"
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: login <username> <password>"
            return when (val result = authenticator.authenticate(args[0], args[1])) {
                is AuthenticationResult.Success -> "OK: zalogowano jako ${result.account.username} (rola: ${result.account.roleId.value})"
                AuthenticationResult.InvalidCredentials -> "Nieprawidłowe poświadczenia"
                AuthenticationResult.AccountBlocked -> "Konto zablokowane"
            }
        }
    }
}
