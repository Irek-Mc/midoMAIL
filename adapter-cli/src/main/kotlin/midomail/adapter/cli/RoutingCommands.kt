package midomail.adapter.cli

import midomail.domain.adapter.AdapterId
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.routing.DeliveryPolicy
import midomail.domain.routing.RoutingDecision
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import java.util.UUID

/**
 * Komendy CLI administracji routingu (SPEC-0024, §4) — lustro `RoutingEndpoints` (`:adapter-rest`,
 * Iteracja 5.13), przez ten sam port. `routing-add`/`routing-update` przyjmują pozycyjne argumenty
 * (nie pełny JSON jak REST) — świadome uproszczenie: `conditions`/`setPriority` niedostępne przez
 * CLI, ustawiane na wartości domyślne (dopasowanie każdego kanału, brak nadpisania priorytetu).
 * Pełna kontrola nad tymi polami wymaga Adaptera REST.
 */
class RoutingCommands(private val routingRuleAdministration: RoutingRuleAdministration) {

    fun commands(): List<CliCommand> = listOf(AddCommand(), UpdateCommand(), RemoveCommand(), SimulateCommand(), HistoryCommand())

    private fun buildRule(args: List<String>): RoutingRule? {
        if (args.size < 5) return null
        return RoutingRule(
            ruleId = RuleId(args[0]),
            priority = args[1].toIntOrNull() ?: return null,
            targetChannel = ChannelType(args[2]),
            targetAdapter = AdapterId(args[3]),
            deliveryPolicy = DeliveryPolicy(args[4]),
            version = RuleVersion("1")
        )
    }

    private inner class AddCommand : CliCommand {
        override val name = "routing-add"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            val rule = buildRule(args) ?: return "Użycie: routing-add <ruleId> <priority> <targetChannel> <targetAdapter> <deliveryPolicy>"
            return try {
                routingRuleAdministration.add(rule)
                "OK"
            } catch (exception: IllegalArgumentException) {
                exception.message ?: "Konflikt"
            }
        }
    }

    private inner class UpdateCommand : CliCommand {
        override val name = "routing-update"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            val rule = buildRule(args) ?: return "Użycie: routing-update <ruleId> <priority> <targetChannel> <targetAdapter> <deliveryPolicy>"
            return try {
                routingRuleAdministration.update(RuleId(args[0]), rule)
                "OK"
            } catch (exception: IllegalArgumentException) {
                exception.message ?: "Nie znaleziono"
            }
        }
    }

    private inner class RemoveCommand : CliCommand {
        override val name = "routing-remove"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            val ruleId = args.firstOrNull() ?: return "Użycie: routing-remove <ruleId>"
            routingRuleAdministration.remove(RuleId(ruleId))
            return "OK"
        }
    }

    private inner class SimulateCommand : CliCommand {
        override val name = "routing-simulate"
        override fun execute(args: List<String>): String {
            if (args.size < 2) return "Użycie: routing-simulate <sourceChannel> <destinationChannel>"
            val message = GatewayMessage(
                identity = Identity(
                    messageId = MessageId(UUID.randomUUID().toString()),
                    correlationId = CorrelationId(UUID.randomUUID().toString()),
                    schemaVersion = SchemaVersion("2.0"),
                    externalReference = ExternalReference("simulate-${UUID.randomUUID()}")
                ),
                source = Channel(type = ChannelType(args[0])),
                destination = Channel(type = ChannelType(args[1])),
                payload = Payload(content = "")
            )
            return when (val decision = routingRuleAdministration.buildEngine().route(message)) {
                is RoutingDecision.Routed -> "matched target=${decision.targetChannel.value}->${decision.targetAdapter.value} policy=${decision.deliveryPolicy.value}"
                RoutingDecision.NoMatch -> "not matched"
            }
        }
    }

    private inner class HistoryCommand : CliCommand {
        override val name = "routing-history"
        override fun execute(args: List<String>): String {
            val history = routingRuleAdministration.history()
            if (history.isEmpty()) return "Brak historii zmian"
            return history.joinToString("\n") { change ->
                "${change.ruleId.value}\t${change.changeType}\tversion=${change.version?.value ?: "-"}\t${change.timestamp}"
            }
        }
    }
}
