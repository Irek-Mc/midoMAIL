package midomail.domain.port

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Potwierdza kontrakt TaskId z SPEC-0013-Scheduler-Provider-Contract.md.
 */
class TaskIdTest {

    @Test
    fun `TaskId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { TaskId("") }
        assertFailsWith<IllegalArgumentException> { TaskId("   ") }
    }
}
