package midomail.domain.adapter

import midomail.domain.port.memory.InMemoryEventPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt Registry z 10-Core/14-Registry-Adapterow.md i
 * SPEC-0010-Plugin-SDK-Contract.md, §Przepływ rejestracji i konfiguracji.
 *
 * Testowany na atrapie [FakeAdapterLifecycle] zgodnej z wąskim interfejsem [AdapterLifecycle]
 * (SPEC-0014-Adapter-Lifecycle-Contract.md, §Zakres w Fazie 1) — żadna prawdziwa implementacja
 * adaptera nie powstaje w Fazie 1.
 */
class RegistryTest {

    private class FakeAdapterLifecycle : AdapterLifecycle {
        var startCalled = false
            private set
        var stopCalled = false
            private set

        override fun start() {
            startCalled = true
        }

        override fun stop() {
            stopCalled = true
        }
    }

    private class FailingOnStartAdapterLifecycle : AdapterLifecycle {
        override fun start() {
            throw IllegalStateException("Symulowana awaria startu (np. brak sieci)")
        }

        override fun stop() {}
    }

    @Test
    fun `register drives the adapter through Registered, Initializing to Ready and calls start`() {
        val publisher = InMemoryEventPublisher()
        val registry = Registry(publisher)
        val adapter = FakeAdapterLifecycle()

        registry.register(AdapterId("email-primary"), adapter)

        assertEquals(AdapterState.READY, registry.stateOf(AdapterId("email-primary")))
        assertTrue(adapter.startCalled)
    }

    @Test
    fun `registering the same AdapterId twice fails`() {
        val registry = Registry(InMemoryEventPublisher())
        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())

        assertFailsWith<IllegalArgumentException> {
            registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())
        }
    }

    @Test
    fun `stop drives the adapter through Stopping to Stopped and calls stop`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapter = FakeAdapterLifecycle()
        registry.register(AdapterId("email-primary"), adapter)

        registry.stop(AdapterId("email-primary"))

        assertEquals(AdapterState.STOPPED, registry.stateOf(AdapterId("email-primary")))
        assertTrue(adapter.stopCalled)
    }

    @Test
    fun `unregister requires the STOPPED state`() {
        val registry = Registry(InMemoryEventPublisher())
        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())

        assertFailsWith<IllegalArgumentException> {
            registry.unregister(AdapterId("email-primary"))
        }
    }

    @Test
    fun `unregister removes a stopped adapter completely`() {
        val registry = Registry(InMemoryEventPublisher())
        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())
        registry.stop(AdapterId("email-primary"))

        registry.unregister(AdapterId("email-primary"))

        assertNull(registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `transitionTo enforces the allowed transition table`() {
        val registry = Registry(InMemoryEventPublisher())
        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())

        registry.transitionTo(AdapterId("email-primary"), AdapterState.BUSY)

        assertEquals(AdapterState.BUSY, registry.stateOf(AdapterId("email-primary")))
    }

    @Test
    fun `transitionTo rejects a disallowed transition`() {
        val registry = Registry(InMemoryEventPublisher())
        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())

        assertFailsWith<IllegalArgumentException> {
            registry.transitionTo(AdapterId("email-primary"), AdapterState.STOPPED)
        }
    }

    @Test
    fun `every state transition publishes a domain event`() {
        val publisher = InMemoryEventPublisher()
        val registry = Registry(publisher)

        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())

        val publishedStates = publisher.events().map { it.payload as AdapterStateChanged }.map { it.state }
        assertEquals(listOf(AdapterState.REGISTERED, AdapterState.INITIALIZING, AdapterState.READY), publishedStates)
    }

    @Test
    fun `all events for one adapter share the same CorrelationId`() {
        val publisher = InMemoryEventPublisher()
        val registry = Registry(publisher)

        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())
        registry.stop(AdapterId("email-primary"))

        val correlationIds = publisher.events().map { it.correlationId }.toSet()
        assertEquals(1, correlationIds.size, "Wszystkie zdarzenia jednego adaptera powinny współdzielić CorrelationId")
    }

    @Test
    fun `stateOf returns null for an unregistered adapter`() {
        val registry = Registry(InMemoryEventPublisher())

        assertNull(registry.stateOf(AdapterId("unknown")))
        assertFalse(registry.stateOf(AdapterId("unknown")) == AdapterState.READY)
    }

    /**
     * Test regresyjny: ADR-0014-Registry-Start-Failure.md — wykryty podczas ręcznej weryfikacji
     * produkcyjnej Fazy 2 na rzeczywistym Gmailu (docs/faza2-weryfikacja-gmail.md, scenariusz 19).
     * `adapter.start()` rzucający wyjątek (np. brak sieci) nie może zniszczyć całego procesu bez
     * śladu — Registry musi wykonać przejście Initializing → Failed przed ponownym rzuceniem.
     */
    @Test
    fun `regresja ADR-0014 - register transitions to Failed and rethrows when start throws`() {
        val publisher = InMemoryEventPublisher()
        val registry = Registry(publisher)

        assertFailsWith<IllegalStateException> {
            registry.register(AdapterId("email-primary"), FailingOnStartAdapterLifecycle())
        }

        assertEquals(AdapterState.FAILED, registry.stateOf(AdapterId("email-primary")))
        val publishedStates = publisher.events().map { it.payload as AdapterStateChanged }.map { it.state }
        assertEquals(listOf(AdapterState.REGISTERED, AdapterState.INITIALIZING, AdapterState.FAILED), publishedStates)
    }

    /**
     * Potwierdza ADR-0018-Registry-Enumeracja.md — dodane w Iteracji 5.1 (Faza 5), amendment
     * addytywny.
     */
    @Test
    fun `registeredAdapterIds is empty when nothing is registered`() {
        val registry = Registry(InMemoryEventPublisher())

        assertEquals(emptySet(), registry.registeredAdapterIds())
    }

    @Test
    fun `registeredAdapterIds reflects live membership as adapters are registered and unregistered`() {
        val registry = Registry(InMemoryEventPublisher())
        registry.register(AdapterId("email-primary"), FakeAdapterLifecycle())
        registry.register(AdapterId("gsm-primary"), FakeAdapterLifecycle())

        assertEquals(setOf(AdapterId("email-primary"), AdapterId("gsm-primary")), registry.registeredAdapterIds())

        registry.stop(AdapterId("email-primary"))
        registry.unregister(AdapterId("email-primary"))

        assertEquals(setOf(AdapterId("gsm-primary")), registry.registeredAdapterIds())
    }

    @Test
    fun `registeredAdapterIds includes an adapter in FAILED state - not just Ready`() {
        val registry = Registry(InMemoryEventPublisher())

        assertFailsWith<IllegalStateException> {
            registry.register(AdapterId("email-primary"), FailingOnStartAdapterLifecycle())
        }

        assertEquals(setOf(AdapterId("email-primary")), registry.registeredAdapterIds())
    }
}
