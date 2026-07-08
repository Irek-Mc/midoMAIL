package midomail.adapter.cli

/**
 * Komenda CLI (ADR-0025-CLI-Parsowanie-Argumentow.md) — [name] jest pierwszym argumentem
 * `args[0]` dyspozycji, pozostałe argumenty trafiają do [execute].
 *
 * [isWriteOperation] (Iteracja 5.16, SPEC-0024 §Uwierzytelnianie i audyt) — `true` dla komend
 * modyfikujących stan (włącz/wyłącz/restart/usuń adaptera, zapis/rollback konfiguracji,
 * dodaj/edytuj/usuń regułę routingu); `false` (domyślnie) dla odczytu i dla `routing-simulate`
 * (bez efektów ubocznych, mimo nazwy sugerującej „operację").
 */
interface CliCommand {
    val name: String
    val isWriteOperation: Boolean get() = false
    fun execute(args: List<String>): String
}
