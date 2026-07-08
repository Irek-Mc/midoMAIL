package midomail.adapter.cli

import midomail.domain.adapter.AdapterId
import midomail.domain.administration.AdapterConfigurationAdministration

/** Komendy CLI typowanej konfiguracji adaptera (SPEC-0025, ADR-0031) — lustro `AdapterConfigurationEndpoints`. */
class AdapterConfigurationCommands(private val administration: AdapterConfigurationAdministration) {

    fun commands(): List<CliCommand> = listOf(GetCommand(), SetCommand())

    private inner class GetCommand : CliCommand {
        override val name = "adapter-config"
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: adapter-config <adapterId> <type>"
            val fields = administration.read(AdapterId(args[0]), args[1])
            if (fields.isEmpty()) return "Nieznany typ adaptera: ${args[1]}"
            return fields.entries.joinToString("\n") { (field, value) -> "$field = ${value ?: "(brak/sekretne)"}" }
        }
    }

    private inner class SetCommand : CliCommand {
        override val name = "adapter-config-set"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            if (args.size < 4) return "Użycie: adapter-config-set <adapterId> <type> <pole> <wartość>"
            return try {
                administration.write(AdapterId(args[0]), args[1], args[2], args.drop(3).joinToString(" "))
                "OK"
            } catch (exception: IllegalArgumentException) {
                exception.message ?: "Nieprawidłowe pole"
            }
        }
    }
}
