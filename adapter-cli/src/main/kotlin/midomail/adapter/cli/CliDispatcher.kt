package midomail.adapter.cli

import midomail.domain.administration.AdminAuditRecorder

/**
 * Dyspozytor komend CLI (ADR-0025-CLI-Parsowanie-Argumentow.md) — zwykłe `Array<String>`, bez
 * biblioteki parsowania argumentów (zbiór komend znany i ograniczony z góry, SPEC-0024).
 *
 * [auditRecorder] (Iteracja 5.16) — wywoływany dla każdej pomyślnie zdyspozycjonowanej komendy
 * zapisu (`CliCommand.isWriteOperation == true`). CLI nie ma warstwy uwierzytelniania (zakłada
 * dostęp z powłoki lokalnej, już uwierzytelnionej na poziomie systemu operacyjnego — w
 * przeciwieństwie do Adaptera REST, wystawionego przez sieć) — `authenticated` przekazywane jako
 * zawsze `true`.
 */
class CliDispatcher(
    private val commands: List<CliCommand>,
    private val auditRecorder: AdminAuditRecorder = AdminAuditRecorder { _, _ -> }
) {

    fun dispatch(args: Array<String>): String {
        if (args.isEmpty()) {
            return "Brak komendy. Dostępne komendy: ${availableCommandNames()}"
        }
        val commandName = args[0]
        val command = commands.find { it.name == commandName }
            ?: return "Nieznana komenda: $commandName. Dostępne komendy: ${availableCommandNames()}"
        val result = command.execute(args.drop(1))
        if (command.isWriteOperation) {
            auditRecorder.record("$commandName ${args.drop(1).joinToString(" ")}".trim(), authenticated = true)
        }
        return result
    }

    private fun availableCommandNames(): String = commands.joinToString(", ") { it.name }
}
