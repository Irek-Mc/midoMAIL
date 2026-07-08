package midomail.adapter.cli

import midomail.domain.health.AlertId
import midomail.domain.notification.EscalationScheduler

/**
 * Komendy CLI administracji alertów (SPEC-0025, ADR-0026) — lustro `AlertEndpoints` (`:adapter-rest`).
 */
class AlertCommands(private val escalationScheduler: EscalationScheduler) {

    fun commands(): List<CliCommand> = listOf(ListCommand(), AcknowledgeCommand())

    private inner class ListCommand : CliCommand {
        override val name = "alerts"
        override fun execute(args: List<String>): String {
            val alerts = escalationScheduler.activeAlerts()
            if (alerts.isEmpty()) return "Brak aktywnych alertów"
            return alerts.joinToString("\n") { alert ->
                "${alert.alertId.value}\tlevel=${alert.level}\tsource=${alert.source.value}\tstatus=${alert.status}"
            }
        }
    }

    private inner class AcknowledgeCommand : CliCommand {
        override val name = "alert-acknowledge"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String {
            val id = args.firstOrNull() ?: return "Użycie: alert-acknowledge <alertId>"
            escalationScheduler.acknowledge(AlertId(id))
            return "OK"
        }
    }
}
