package midomail.domain.error

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterLifecycle
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Registry
import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import midomail.domain.message.CorrelationId
import midomail.domain.port.memory.InMemoryEventPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Potwierdza kontrakt klasyfikacji błędów (SPEC-0022-Error-Classification-Contract.md).
 */
class GatewayErrorTest {

    @Test
    fun `AdapterError carries the failing adapterId`() {
        val error = GatewayError.AdapterError(adapterId = AdapterId("email-primary"), message = "połączenie IMAP nie powiodło się")

        assertEquals(AdapterId("email-primary"), error.adapterId)
        assertEquals("połączenie IMAP nie powiodło się", error.message)
    }

    @Test
    fun `correlationId and cause default to null`() {
        val error = GatewayError.DomainError(message = "brak trasy")

        assertNull(error.correlationId)
        assertNull(error.cause)
    }

    @Test
    fun `CriticalError maps to a CRITICAL ACTIVE Alert carrying the same correlationId`() {
        val error = GatewayError.CriticalError(
            message = "Rejestracja adaptera nie powiodła się",
            correlationId = CorrelationId("correlation-1")
        )

        val alert = error.toAlert(SourceComponent("email-primary"))

        assertEquals(AlertLevel.CRITICAL, alert.level)
        assertEquals(AlertStatus.ACTIVE, alert.status)
        assertEquals(CorrelationId("correlation-1"), alert.correlationId)
        assertEquals("Rejestracja adaptera nie powiodła się", alert.recommendedAction)
    }

    /**
     * Regresja (wzorem testu ADR-0014-Registry-Start-Failure.md): `Registry.register()` poprawnie
     * łapie wyjątek z `adapter.start()` i przechodzi do `FAILED` przed ponownym rzuceniem — ten
     * test potwierdza, że WYWOŁUJĄCY (punkt kompozycji, wzorem `registerSafely()` z Fazy 3) może
     * dodatkowo sklasyfikować złapany wyjątek jako `GatewayError.CriticalError` i uzyskać z niego
     * `Alert`, bez żadnej zmiany w samym `Registry` (SPEC-0022, §Gdzie klasyfikacja zachodzi).
     */
    @Test
    fun `an adapter start failure is both a Registry FAILED transition and a classified CriticalError`() {
        val registry = Registry(InMemoryEventPublisher())
        val adapterId = AdapterId("email-primary")
        val failingAdapter = object : AdapterLifecycle {
            override fun start() {
                throw IllegalStateException("Połączenie IMAP nie powiodło się")
            }
            override fun stop() {}
        }

        var classifiedError: GatewayError? = null
        var alert: Alert? = null
        try {
            registry.register(adapterId, failingAdapter)
        } catch (exception: Exception) {
            classifiedError = GatewayError.CriticalError(
                message = exception.message ?: "Nieznany błąd rejestracji",
                cause = exception
            )
            alert = (classifiedError as GatewayError.CriticalError).toAlert(SourceComponent(adapterId.value))
        }

        assertEquals(AdapterState.FAILED, registry.stateOf(adapterId))
        assertTrue(classifiedError is GatewayError.CriticalError)
        assertEquals(AlertLevel.CRITICAL, alert?.level)
        assertEquals(AlertStatus.ACTIVE, alert?.status)
    }
}
