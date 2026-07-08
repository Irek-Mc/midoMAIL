package midomail.domain.adapter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Potwierdza tabelę przejść z SPEC-0014-Adapter-Lifecycle-Contract.md.
 */
class AdapterStateTest {

    @Test
    fun `happy path - Registered to Ready via Initializing`() {
        assertTrue(AdapterState.REGISTERED.canTransitionTo(AdapterState.INITIALIZING))
        assertTrue(AdapterState.INITIALIZING.canTransitionTo(AdapterState.READY))
    }

    @Test
    fun `happy path - Stopping to Stopped`() {
        assertTrue(AdapterState.STOPPING.canTransitionTo(AdapterState.STOPPED))
    }

    @Test
    fun `Ready can transition to Busy and back`() {
        assertTrue(AdapterState.READY.canTransitionTo(AdapterState.BUSY))
        assertTrue(AdapterState.BUSY.canTransitionTo(AdapterState.READY))
    }

    @Test
    fun `Ready, Busy and Degraded can all transition to Degraded and recover`() {
        assertTrue(AdapterState.READY.canTransitionTo(AdapterState.DEGRADED))
        assertTrue(AdapterState.BUSY.canTransitionTo(AdapterState.DEGRADED))
        assertTrue(AdapterState.DEGRADED.canTransitionTo(AdapterState.READY))
        assertTrue(AdapterState.DEGRADED.canTransitionTo(AdapterState.BUSY))
    }

    @Test
    fun `Initializing, Ready, Busy and Degraded can all fail`() {
        assertTrue(AdapterState.INITIALIZING.canTransitionTo(AdapterState.FAILED))
        assertTrue(AdapterState.READY.canTransitionTo(AdapterState.FAILED))
        assertTrue(AdapterState.BUSY.canTransitionTo(AdapterState.FAILED))
        assertTrue(AdapterState.DEGRADED.canTransitionTo(AdapterState.FAILED))
    }

    @Test
    fun `Failed can only transition to Stopping - not directly back to Ready`() {
        assertTrue(AdapterState.FAILED.canTransitionTo(AdapterState.STOPPING))
        assertFalse(AdapterState.FAILED.canTransitionTo(AdapterState.READY))
    }

    @Test
    fun `Ready, Busy, Degraded and Failed can all be stopped administratively`() {
        assertTrue(AdapterState.READY.canTransitionTo(AdapterState.STOPPING))
        assertTrue(AdapterState.BUSY.canTransitionTo(AdapterState.STOPPING))
        assertTrue(AdapterState.DEGRADED.canTransitionTo(AdapterState.STOPPING))
        assertTrue(AdapterState.FAILED.canTransitionTo(AdapterState.STOPPING))
    }

    @Test
    fun `Stopped is terminal - no outgoing transitions`() {
        AdapterState.entries.forEach { target ->
            assertFalse(AdapterState.STOPPED.canTransitionTo(target))
        }
    }

    @Test
    fun `Registered cannot skip directly to Ready`() {
        assertFalse(AdapterState.REGISTERED.canTransitionTo(AdapterState.READY))
    }
}
