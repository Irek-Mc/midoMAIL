package midomail.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza dyspozytor komend CLI (ADR-0025) — rzeczywiste wywołanie `dispatch(args)` z
 * konstruowaną tablicą argumentów, tym samym kształtem co `main(args: Array<String>)`.
 */
class CliDispatcherTest {

    private class PingCommand : CliCommand {
        override val name = "ping"
        override fun execute(args: List<String>): String = "pong ${args.joinToString(" ")}".trim()
    }

    @Test
    fun `dispatch routes to the matching command and passes remaining arguments`() {
        val dispatcher = CliDispatcher(listOf(PingCommand()))

        val result = dispatcher.dispatch(arrayOf("ping", "hello", "world"))

        assertEquals("pong hello world", result)
    }

    @Test
    fun `dispatch with no arguments returns a readable message listing available commands`() {
        val dispatcher = CliDispatcher(listOf(PingCommand()))

        val result = dispatcher.dispatch(emptyArray())

        assertTrue(result.contains("ping"))
    }

    @Test
    fun `dispatch with an unknown command name returns a readable message, not an exception`() {
        val dispatcher = CliDispatcher(listOf(PingCommand()))

        val result = dispatcher.dispatch(arrayOf("unknown-command"))

        assertTrue(result.contains("Nieznana komenda"))
        assertTrue(result.contains("ping"))
    }
}
