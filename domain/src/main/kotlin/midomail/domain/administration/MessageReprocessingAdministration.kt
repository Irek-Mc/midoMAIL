package midomail.domain.administration

import midomail.domain.gateway.GatewayInbound
import midomail.domain.gateway.ProcessingResult
import midomail.domain.message.CausationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.MessageId
import midomail.domain.port.MessageStore
import midomail.domain.processing.ProcessingContext
import java.util.UUID

/**
 * Ręczne ponowne przetworzenie komunikatu (ADR-0028-Message-Reprocessing.md,
 * 62-Komunikaty.md §5, SPEC-0008 §Ręczne ponowne przetworzenie) — jawnie unieważnia rekord
 * deduplikacji dla [ExternalReference], a następnie przekazuje NOWY [GatewayMessage] (ta sama
 * treść, nowy [MessageId], [CausationId] wskazujący na oryginał) przez [GatewayInbound.receive] —
 * przechodzi przez `ExactlyOnceEngine`/`RoutingEngine` normalnie, nie omija ich.
 */
class MessageReprocessingAdministration(
    private val messageStore: MessageStore,
    private val gatewayInbound: GatewayInbound
) {
    sealed class ReprocessResult {
        data class Reprocessed(val message: GatewayMessage) : ReprocessResult()
        data object NotFound : ReprocessResult()
        data class Failed(val reason: String) : ReprocessResult()
    }

    fun reprocess(externalReference: ExternalReference): ReprocessResult {
        val existing = messageStore.findByExternalReference(externalReference)
            ?: return ReprocessResult.NotFound

        messageStore.invalidateDeduplication(externalReference)

        val fresh = existing.copy(
            identity = existing.identity.copy(
                messageId = MessageId(UUID.randomUUID().toString()),
                causationId = CausationId(existing.identity.messageId.value)
            ),
            processingContext = ProcessingContext()
        )

        return when (val result = gatewayInbound.receive(fresh)) {
            is ProcessingResult.Accepted -> ReprocessResult.Reprocessed(result.message)
            is ProcessingResult.NoRoute -> ReprocessResult.Failed("Brak trasy routingu dla ponownie przetworzonego komunikatu")
            is ProcessingResult.Rejected -> ReprocessResult.Failed(result.reason)
            ProcessingResult.Duplicate ->
                ReprocessResult.Failed("Nadal wykryto jako duplikat mimo unieważnienia rekordu deduplikacji")
        }
    }
}
