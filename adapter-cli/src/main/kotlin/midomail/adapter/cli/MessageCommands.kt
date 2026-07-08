package midomail.adapter.cli

import midomail.domain.administration.MessageReprocessingAdministration
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.MessageId
import midomail.domain.port.MessageQueryFilter
import midomail.domain.port.MessageSort
import midomail.domain.port.MessageStore
import midomail.domain.port.PageRequest

/**
 * Komendy CLI Komunikatów (SPEC-0025, 62-Komunikaty.md) — lustro `MessageEndpoints`
 * (`:adapter-rest`), przez ten sam port `MessageStore`/`MessageReprocessingAdministration`.
 */
class MessageCommands(
    private val messageStore: MessageStore,
    private val reprocessingAdministration: MessageReprocessingAdministration
) {
    fun commands(): List<CliCommand> = listOf(ListCommand(), FindCommand(), ReprocessCommand())

    /**
     * Świadome uproszczenie względem REST: przyjmuje wyłącznie `channelType`/`contentSearch`
     * (nie wszystkie 9 pól `MessageQueryFilter`) — ten sam kompromis co `routing-add`/`routing-update`
     * przez CLI w Fazie 5 (pozycyjne argumenty zamiast pełnej ekspresji JSON).
     */
    private inner class ListCommand : CliCommand {
        override val name = "messages"
        override fun execute(args: List<String>): String {
            val channelType = args.getOrNull(0)?.let { midomail.domain.message.ChannelType(it) }
            val contentSearch = args.getOrNull(1)
            val page = messageStore.query(
                filter = MessageQueryFilter(channelType = channelType, contentSearch = contentSearch),
                sort = MessageSort(),
                page = PageRequest()
            )
            if (page.items.isEmpty()) return "Brak komunikatów"
            return page.items.joinToString("\n") { describe(it) }
        }
    }

    private fun describe(message: midomail.domain.message.GatewayMessage): String =
        "${message.identity.messageId.value}\tcorrelationId=${message.identity.correlationId.value}\t" +
            "externalReference=${message.identity.externalReference.value}\t${message.source.type.value}->${message.destination.type.value}\t" +
            "state=${message.processingContext.processingState}"

    private inner class FindCommand : CliCommand {
        override val name = "messages-find"
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: messages-find <messageId|externalReference|correlationId> <wartość>"
            val message = when (args[0]) {
                "messageId" -> messageStore.findById(MessageId(args[1]))
                "externalReference" -> messageStore.findByExternalReference(ExternalReference(args[1]))
                "correlationId" -> messageStore.findByCorrelationId(CorrelationId(args[1])).firstOrNull()
                else -> return "Nieznany typ wyszukiwania: ${args[0]} (dostępne: messageId, externalReference, correlationId)"
            }
            return message?.let { describe(it) } ?: "Nie znaleziono komunikatu"
        }
    }

    private inner class ReprocessCommand : CliCommand {
        override val name = "messages-reprocess"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            val externalReference = args.firstOrNull() ?: return "Użycie: messages-reprocess <externalReference>"
            return when (val result = reprocessingAdministration.reprocess(ExternalReference(externalReference))) {
                is MessageReprocessingAdministration.ReprocessResult.Reprocessed -> "OK: nowy MessageId=${result.message.identity.messageId.value}"
                MessageReprocessingAdministration.ReprocessResult.NotFound -> "Nie znaleziono komunikatu o ExternalReference: $externalReference"
                is MessageReprocessingAdministration.ReprocessResult.Failed -> "Nieudane: ${result.reason}"
            }
        }
    }
}
