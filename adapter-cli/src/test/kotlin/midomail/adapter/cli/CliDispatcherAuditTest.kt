package midomail.adapter.cli

import midomail.domain.administration.AdminAuditRecorder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza integrację audytu w `CliDispatcher` (Iteracja 5.16).
 */
class CliDispatcherAuditTest {

    private class WriteCommand : CliCommand {
        override val name = "write-thing"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String = "done"
    }

    private class ReadCommand : CliCommand {
        override val name = "read-thing"
        override fun execute(args: List<String>): String = "value"
    }

    @Test
    fun `a write command execution is recorded exactly once`() {
        val recorded = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val dispatcher = CliDispatcher(listOf(WriteCommand()), AdminAuditRecorder { op, authenticated -> recorded.add(op to authenticated) })

        dispatcher.dispatch(arrayOf("write-thing", "arg1"))

        assertEquals(1, recorded.size)
        assertTrue(recorded.single().second)
        assertTrue(recorded.single().first.contains("write-thing"))
    }

    @Test
    fun `a read command execution is not recorded`() {
        val recorded = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val dispatcher = CliDispatcher(listOf(ReadCommand()), AdminAuditRecorder { op, authenticated -> recorded.add(op to authenticated) })

        dispatcher.dispatch(arrayOf("read-thing"))

        assertEquals(0, recorded.size)
    }
}
