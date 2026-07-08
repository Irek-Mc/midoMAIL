package midomail.domain.processing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Potwierdza reguły przejść z SPEC-0004-Processing-State.md.
 */
class ProcessingStateTest {

    @Test
    fun `happy path sequence is allowed step by step`() {
        assertTrue(ProcessingState.ACCEPTED.canTransitionTo(ProcessingState.VALIDATED))
        assertTrue(ProcessingState.VALIDATED.canTransitionTo(ProcessingState.ROUTED))
        assertTrue(ProcessingState.ROUTED.canTransitionTo(ProcessingState.SCHEDULED))
        assertTrue(ProcessingState.SCHEDULED.canTransitionTo(ProcessingState.PROCESSING))
        assertTrue(ProcessingState.PROCESSING.canTransitionTo(ProcessingState.HANDED_TO_ADAPTER))
        assertTrue(ProcessingState.HANDED_TO_ADAPTER.canTransitionTo(ProcessingState.SENT))
        assertTrue(ProcessingState.SENT.canTransitionTo(ProcessingState.DELIVERED))
        assertTrue(ProcessingState.DELIVERED.canTransitionTo(ProcessingState.COMPLETED))
    }

    @Test
    fun `SENT can skip DELIVERED and go directly to COMPLETED when transport does not confirm delivery`() {
        assertTrue(ProcessingState.SENT.canTransitionTo(ProcessingState.COMPLETED))
    }

    @Test
    fun `SCHEDULED can self-transition for throttling retries`() {
        assertTrue(ProcessingState.SCHEDULED.canTransitionTo(ProcessingState.SCHEDULED))
    }

    @Test
    fun `transitions skipping steps are rejected`() {
        assertFalse(ProcessingState.ACCEPTED.canTransitionTo(ProcessingState.ROUTED))
        assertFalse(ProcessingState.ACCEPTED.canTransitionTo(ProcessingState.SCHEDULED))
        assertFalse(ProcessingState.VALIDATED.canTransitionTo(ProcessingState.SCHEDULED))
    }

    @Test
    fun `FAILED and CANCELLED are reachable from every non-terminal state`() {
        val nonTerminal = listOf(
            ProcessingState.ACCEPTED,
            ProcessingState.VALIDATED,
            ProcessingState.ROUTED,
            ProcessingState.SCHEDULED,
            ProcessingState.PROCESSING,
            ProcessingState.HANDED_TO_ADAPTER,
            ProcessingState.SENT
        )

        nonTerminal.forEach { state ->
            assertTrue(state.canTransitionTo(ProcessingState.FAILED), "$state -> FAILED powinno być dozwolone")
            assertTrue(state.canTransitionTo(ProcessingState.CANCELLED), "$state -> CANCELLED powinno być dozwolone")
        }
    }

    @Test
    fun `terminal states cannot transition anywhere - including to themselves`() {
        val terminal = listOf(ProcessingState.COMPLETED, ProcessingState.FAILED, ProcessingState.CANCELLED)

        terminal.forEach { terminalState ->
            ProcessingState.entries.forEach { candidate ->
                assertFalse(
                    terminalState.canTransitionTo(candidate),
                    "$terminalState nie powinien mieć żadnych przejść wychodzących (próba do $candidate)"
                )
            }
        }
    }

    @Test
    fun `DELIVERED can only transition to COMPLETED`() {
        assertTrue(ProcessingState.DELIVERED.canTransitionTo(ProcessingState.COMPLETED))
        assertFalse(ProcessingState.DELIVERED.canTransitionTo(ProcessingState.FAILED))
        assertFalse(ProcessingState.DELIVERED.canTransitionTo(ProcessingState.CANCELLED))
    }
}
