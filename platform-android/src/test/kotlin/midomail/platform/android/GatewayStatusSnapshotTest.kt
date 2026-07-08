package midomail.platform.android

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class GatewayStatusSnapshotTest {

    private class FakeAdapter(
        override val adapterId: AdapterId,
        private val healthy: Boolean,
        private val sent: Long,
        private val received: Long
    ) : Adapter {
        override val adapterVersion = "1.0"
        override fun start() {}
        override fun stop() {}
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = healthy)
        override fun metrics(): Metrics = Metrics(null, 0, 0, sent, received, 0)
        override fun send(message: GatewayMessage) {}
    }

    @Test
    fun `builds one snapshot per adapter with its id, health, and message counters`() {
        val adapters = listOf(
            FakeAdapter(AdapterId("gsm-primary"), healthy = true, sent = 5, received = 3),
            FakeAdapter(AdapterId("email-primary"), healthy = false, sent = 0, received = 0)
        )

        val snapshots = buildAdapterSnapshots(adapters)

        assertEquals(
            listOf(
                AdapterSnapshot("gsm-primary", healthy = true, messagesSent = 5, messagesReceived = 3),
                AdapterSnapshot("email-primary", healthy = false, messagesSent = 0, messagesReceived = 0)
            ),
            snapshots
        )
    }

    @Test
    fun `returns an empty list for no registered adapters`() {
        assertEquals(emptyList(), buildAdapterSnapshots(emptyList()))
    }
}
