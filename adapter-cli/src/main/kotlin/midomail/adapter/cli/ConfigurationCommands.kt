package midomail.adapter.cli

import midomail.domain.port.ConfigurationProvider

/**
 * Komendy CLI zapisu konfiguracji (SPEC-0024, §3) — lustro `ConfigurationEndpoints`
 * (`:adapter-rest`, Iteracja 5.12), przez ten sam port. Głębokość zgodna z ADR-0020.
 */
class ConfigurationCommands(private val configurationProvider: ConfigurationProvider) {

    fun commands(): List<CliCommand> = listOf(SetCommand(), RollbackCommand())

    private inner class SetCommand : CliCommand {
        override val name = "config-set"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: config-set <klucz> <wartość>"
            val key = args[0]
            val value = args.drop(1).joinToString(" ")
            configurationProvider.setValue(key, value)
            return "OK: $key = $value"
        }
    }

    private inner class RollbackCommand : CliCommand {
        override val name = "config-rollback"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            val key = args.firstOrNull() ?: return "Użycie: config-rollback <klucz>"
            val restored = configurationProvider.rollback(key) ?: return "Brak historii do przywrócenia dla klucza: $key"
            return "OK: $key = $restored"
        }
    }
}
