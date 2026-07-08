package midomail.adapter.cli

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters
import midomail.domain.administration.RoutingRuleAdministration
import midomail.domain.diagnostics.DiagnosticsFacade
import midomail.domain.message.CorrelationId
import midomail.domain.port.ConfigurationProvider
import midomail.domain.statistics.StatisticsAggregator

/**
 * Komendy CLI odczytu stanu (SPEC-0024, §1 Odczyt stanu) — te same operacje co
 * `ReadStateEndpoints` (`:adapter-rest`, Iteracja 5.10), przez TE SAME porty administracyjne
 * (`:domain`), zero zmian w Core. Konkretny dowód Roadmapy §7(a): drugi adapter (CLI) konsumuje
 * identyczne kontrakty bez żadnych zmian w tym, co zbudowano w Części A.
 *
 * Format wyjścia: zwykły tekst, jedna pozycja na wiersz (ADR-0025: CLI nie serializuje do JSON,
 * w przeciwieństwie do REST).
 */
class ReadStateCommands(
    private val registry: Registry,
    private val managedAdapters: ManagedAdapters,
    private val routingRuleAdministration: RoutingRuleAdministration,
    private val configurationProvider: ConfigurationProvider,
    private val statisticsAggregator: StatisticsAggregator,
    private val diagnosticsFacade: DiagnosticsFacade
) {
    fun commands(): List<CliCommand> = listOf(
        AdaptersCommand(),
        RoutingRulesCommand(),
        ConfigCommand(),
        StatisticsCommand(),
        DiagnosticsTraceCommand()
    )

    private inner class AdaptersCommand : CliCommand {
        override val name = "adapters"
        override fun execute(args: List<String>): String {
            val requestedId = args.firstOrNull()
            val adapters = managedAdapters.all().filter { requestedId == null || it.adapterId.value == requestedId }
            if (adapters.isEmpty()) return "Brak adapterów"
            return adapters.joinToString("\n") { describe(it) }
        }

        private fun describe(adapter: Adapter): String {
            val health = adapter.health()
            val metrics = adapter.metrics()
            val state = registry.stateOf(adapter.adapterId)?.name ?: "UNKNOWN"
            return "${adapter.adapterId.value}\tstate=$state\thealthy=${health.healthy}\t" +
                "sent=${metrics.messagesSent}\treceived=${metrics.messagesReceived}\terrors=${metrics.errorCount}"
        }
    }

    private inner class RoutingRulesCommand : CliCommand {
        override val name = "routing-rules"
        override fun execute(args: List<String>): String {
            val rules = routingRuleAdministration.list()
            if (rules.isEmpty()) return "Brak reguł routingu"
            return rules.joinToString("\n") { rule ->
                "${rule.ruleId.value}\tpriority=${rule.priority}\tenabled=${rule.enabled}\t" +
                    "target=${rule.targetChannel.value}->${rule.targetAdapter.value}\tversion=${rule.version.value}"
            }
        }
    }

    private inner class ConfigCommand : CliCommand {
        override val name = "config"
        override fun execute(args: List<String>): String {
            val key = args.firstOrNull() ?: return "Użycie: config <klucz>"
            val value = configurationProvider.getValue(key) ?: "(brak wartości)"
            val history = configurationProvider.history(key)
            return "$key = $value\nhistoria: ${if (history.isEmpty()) "(brak)" else history.joinToString(", ")}"
        }
    }

    private inner class StatisticsCommand : CliCommand {
        override val name = "statistics"
        override fun execute(args: List<String>): String {
            val requestedId = args.firstOrNull()
            val snapshots = statisticsAggregator.snapshots().filter { requestedId == null || it.adapterId.value == requestedId }
            if (snapshots.isEmpty()) return "Brak migawek statystyk"
            return snapshots.joinToString("\n") { snapshot ->
                "${snapshot.adapterId.value}\t${snapshot.periodStart}..${snapshot.periodEnd}\t" +
                    "sent=${snapshot.messagesSent}\treceived=${snapshot.messagesReceived}\terrors=${snapshot.errorCount}"
            }
        }
    }

    private inner class DiagnosticsTraceCommand : CliCommand {
        override val name = "diagnostics-trace"
        override fun execute(args: List<String>): String {
            val correlationId = args.firstOrNull() ?: return "Użycie: diagnostics-trace <correlationId>"
            val trace = diagnosticsFacade.messageTrace(CorrelationId(correlationId))
            val messages = "Wiadomości: ${trace.messages.joinToString(", ") { it.identity.messageId.value }.ifEmpty { "(brak)" }}"
            val events = "Zdarzenia: ${trace.events.joinToString(", ") { it.eventType.value }.ifEmpty { "(brak)" }}"
            return "$messages\n$events"
        }
    }
}
