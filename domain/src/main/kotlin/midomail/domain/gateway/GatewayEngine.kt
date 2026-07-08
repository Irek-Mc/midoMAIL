package midomail.domain.gateway

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.exactlyonce.ExactlyOnceEngine
import midomail.domain.exactlyonce.ExactlyOnceResult
import midomail.domain.message.GatewayMessage
import midomail.domain.port.EventPublisher
import midomail.domain.port.GatewayOutbound
import midomail.domain.processing.ProcessingState
import midomail.domain.routing.RoutingDecision
import midomail.domain.routing.RoutingEngine
import java.time.Instant
import java.util.UUID

/** Wynik przyjęcia komunikatu przez Gateway Engine (10-Core/11-Gateway-Engine.md, §5). */
sealed class ProcessingResult {
    data class Accepted(val message: GatewayMessage) : ProcessingResult()
    data class Rejected(val reason: String) : ProcessingResult()
    data object Duplicate : ProcessingResult()
    data class NoRoute(val message: GatewayMessage) : ProcessingResult()
}

/**
 * Gateway Engine — jedyne miejsce realizacji logiki biznesowej Communication Gateway
 * (10-Core/11-Gateway-Engine.md).
 *
 * Realizuje pełny przepływ z §5 („Przepływ przetwarzania"):
 * 1. Odebranie komunikatu przez port wejściowy — parametr [receive].
 * 2. Walidacja modelu domenowego — komunikat musi być w stanie [ProcessingState.ACCEPTED].
 * 3. Utworzenie kontekstu przetwarzania — przejście do [ProcessingState.VALIDATED].
 * 4. Weryfikacja polityki Exactly Once — [ExactlyOnceEngine.processIfNew]; duplikat przerywa dalsze
 *    przetwarzanie ([ProcessingResult.Duplicate]).
 * 5. Wyznaczenie trasy przez Routing Engine — [RoutingEngine.route]; brak trasy kończy się stanem
 *    [ProcessingState.FAILED] ([ProcessingResult.NoRoute]).
 * 6. Publikacja zdarzeń domenowych — [EventPublisher.publish].
 * 7. Przekazanie komunikatu do portu wyjściowego — [GatewayOutbound.send] (Faza 1: atrapa, żaden
 *    rzeczywisty adapter jeszcze nie istnieje).
 * 8. Aktualizacja stanu przetwarzania — przejście do [ProcessingState.SENT].
 *
 * Kroki 5, 7 i 8 przechodzą przez [ProcessingState.ROUTED] → [ProcessingState.SCHEDULED] →
 * [ProcessingState.PROCESSING] → [ProcessingState.HANDED_TO_ADAPTER] → [ProcessingState.SENT] —
 * jedyna kolejność dozwolona przez zamrożoną tabelę przejść (Iteracja 2); żaden krok jej nie omija.
 *
 * Poza zakresem Fazy 1 (świadome ograniczenie, nie luka): pośrednie stany przetwarzania nie są
 * zapisywane z powrotem do Message Store po każdym kroku — jedynie [ExactlyOnceEngine] zapisuje
 * komunikat raz, przy pierwszym przyjęciu (krok 4). Synchronizacja każdego pośredniego przejścia
 * z trwałym magazynem jest przewidziana dla fazy, w której pojawi się rzeczywisty konsument tej
 * informacji (np. Dashboard UI, Faza 6).
 */
class GatewayEngine(
    private val exactlyOnceEngine: ExactlyOnceEngine,
    private val routingEngine: RoutingEngine,
    private val eventPublisher: EventPublisher,
    private val gatewayOutbound: GatewayOutbound
) : GatewayInbound {

    override fun receive(message: GatewayMessage): ProcessingResult {
        if (message.processingContext.processingState != ProcessingState.ACCEPTED) {
            return ProcessingResult.Rejected(
                "Nowo odebrany komunikat musi być w stanie ACCEPTED, otrzymano: ${message.processingContext.processingState}"
            )
        }

        val validated = withState(message, ProcessingState.VALIDATED)

        val exactlyOnceResult = exactlyOnceEngine.processIfNew(validated.identity.externalReference, validated)
        if (exactlyOnceResult is ExactlyOnceResult.Duplicate) {
            return ProcessingResult.Duplicate
        }
        val afterExactlyOnce = (exactlyOnceResult as ExactlyOnceResult.Accepted).message

        val routingDecision = routingEngine.route(afterExactlyOnce)
        if (routingDecision is RoutingDecision.NoMatch) {
            return ProcessingResult.NoRoute(withState(afterExactlyOnce, ProcessingState.FAILED))
        }
        routingDecision as RoutingDecision.Routed

        val routed = withState(
            afterExactlyOnce.copy(
                destination = afterExactlyOnce.destination.copy(
                    type = routingDecision.targetChannel,
                    adapterId = routingDecision.targetAdapter
                ),
                identity = afterExactlyOnce.identity.copy(messagePriority = routingDecision.messagePriority)
            ),
            ProcessingState.ROUTED
        )

        publishRoutedEvent(routed)

        val handedToAdapter = withState(
            withState(withState(routed, ProcessingState.SCHEDULED), ProcessingState.PROCESSING),
            ProcessingState.HANDED_TO_ADAPTER
        )
        gatewayOutbound.send(handedToAdapter)

        val sent = withState(handedToAdapter, ProcessingState.SENT)
        return ProcessingResult.Accepted(sent)
    }

    private fun withState(message: GatewayMessage, state: ProcessingState): GatewayMessage =
        message.copy(processingContext = message.processingContext.copy(processingState = state))

    private fun publishRoutedEvent(message: GatewayMessage) {
        eventPublisher.publish(
            Event(
                eventId = EventId(UUID.randomUUID().toString()),
                eventType = EventType("domain.message_routed"),
                eventVersion = EventVersion("1.0"),
                category = EventCategory.DOMAIN,
                timestamp = Instant.now(),
                correlationId = message.identity.correlationId,
                causationId = null,
                sourceComponent = SourceComponent("GatewayEngine"),
                payload = message
            )
        )
    }
}
