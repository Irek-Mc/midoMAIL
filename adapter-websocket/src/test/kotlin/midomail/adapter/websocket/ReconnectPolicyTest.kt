package midomail.adapter.websocket

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReconnectPolicyTest {

    @Test
    fun `negative maxAttempts is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ReconnectPolicy(maxAttempts = -1, backoffMillis = 1000)
        }
    }

    @Test
    fun `zero maxAttempts is accepted (no retries)`() {
        ReconnectPolicy(maxAttempts = 0, backoffMillis = 1000)
    }

    @Test
    fun `non-positive backoffMillis is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ReconnectPolicy(maxAttempts = 3, backoffMillis = 0)
        }
    }
}
