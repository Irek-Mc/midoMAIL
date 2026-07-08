package midomail.adapter.rest

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Capability
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Metrics
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.AdminAuthenticator
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.memory.InMemoryEventPublisher
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Potwierdza endpointy zarządzania adapterami (SPEC-0024, §2) na prawdziwym serwerze/kliencie.
 */
class AdapterManagementEndpointsTest {

    private lateinit var server: AdminHttpServer

    private class FakeAdapter(override val adapterId: AdapterId) : Adapter {
        override val adapterVersion = "1.0"
        var startCount = 0
        var stopCount = 0

        override fun start() {
            startCount++
        }
        override fun stop() {
            stopCount++
        }
        override fun supportedChannels(): Set<Channel> = emptySet()
        override fun supportedCapabilities(): Set<Capability> = emptySet()
        override fun health(): HealthStatus = HealthStatus(healthy = true)
        override fun metrics(): Metrics = Metrics(null, 0, 0, 0, 0, 0)
        override fun send(message: GatewayMessage) {}
    }

    private class AllowAllAuthenticator : AdminAuthenticator {
        override fun authenticate(providedKey: String): Boolean = true
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun setup(): Triple<Registry, ManagedAdapters, FakeAdapter> {
        val registry = Registry(InMemoryEventPublisher())
        val adapter = FakeAdapter(AdapterId("email-primary"))
        registry.register(AdapterId("email-primary"), adapter)
        val managedAdapters = ManagedAdapters(listOf(adapter))

        server = AdminHttpServer(port = 0, authenticator = AllowAllAuthenticator())
        AdapterManagementEndpoints(registry, managedAdapters).registerRoutes(server)
        server.start()

        return Triple(registry, managedAdapters, adapter)
    }

    private fun post(path: String): Int {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        connection.doOutput = true
        connection.outputStream.close()
        return connection.responseCode
    }

    private fun delete(path: String): Int {
        val connection = URL("http://localhost:${server.port()}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty(AdminHttpServer.API_KEY_HEADER, "any-key")
        return connection.responseCode
    }

    @Test
    fun `disable transitions the adapter to DEGRADED`() {
        val (registry, _, _) = setup()

        val statusCode = post("/adapters/disable?id=email-primary")

        assertEquals(200, statusCode)
        assertEquals(AdapterState.DEGRADED, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `enable transitions a disabled adapter back to READY`() {
        val (registry, _, _) = setup()
        post("/adapters/disable?id=email-primary")

        val statusCode = post("/adapters/enable?id=email-primary")

        assertEquals(200, statusCode)
        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `restart calls stop and start again on the same adapter instance`() {
        val (registry, _, adapter) = setup()

        val statusCode = post("/adapters/restart?id=email-primary")

        assertEquals(200, statusCode)
        assertEquals(1, adapter.stopCount)
        assertEquals(2, adapter.startCount, "start() wywołane raz przy rejestracji, raz przy restarcie")
        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `test connection returns health without changing adapter state`() {
        val (registry, _, _) = setup()

        val statusCode = post("/adapters/test?id=email-primary")

        assertEquals(200, statusCode)
        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `delete stops, unregisters and removes the adapter from ManagedAdapters`() {
        val (registry, managedAdapters, _) = setup()

        val statusCode = delete("/adapters?id=email-primary")

        assertEquals(200, statusCode)
        assertNull(registry.stateOf(AdapterId("email-primary")))
        assertNull(managedAdapters.get(AdapterId("email-primary")))
    }

    @Test
    fun `adding an adapter through the API is explicitly unsupported - 501`() {
        setup()

        val statusCode = post("/adapters")

        assertEquals(501, statusCode)
    }

    @Test
    fun `an operation without an id query parameter returns 400`() {
        setup()

        val statusCode = post("/adapters/disable")

        assertEquals(400, statusCode)
    }

    @Test
    fun `an operation on an unknown adapter id returns 404`() {
        setup()

        val statusCode = post("/adapters/disable?id=unknown")

        assertEquals(404, statusCode)
    }
}
