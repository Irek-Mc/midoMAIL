package midomail.adapter.cli

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters

/**
 * Komendy CLI zarządzania adapterami (SPEC-0024, §2) — lustro `AdapterManagementEndpoints`
 * (`:adapter-rest`, Iteracja 5.11), przez te same porty. Mapowanie na cykl życia identyczne
 * (patrz komentarz `AdapterManagementEndpoints`).
 */
class AdapterManagementCommands(
    private val registry: Registry,
    private val managedAdapters: ManagedAdapters
) {
    fun commands(): List<CliCommand> = listOf(
        EnableCommand(), DisableCommand(), RestartCommand(), TestCommand(), DeleteCommand(), AddCommand()
    )

    private fun withAdapter(args: List<String>, action: (AdapterId, midomail.domain.adapter.Adapter) -> String): String {
        val id = args.firstOrNull() ?: return "Użycie: <komenda> <adapterId>"
        val adapterId = AdapterId(id)
        val adapter = managedAdapters.get(adapterId) ?: return "Nieznany adapter: $id"
        return try {
            action(adapterId, adapter)
        } catch (exception: Exception) {
            exception.message ?: "Operacja nie powiodła się"
        }
    }

    private inner class EnableCommand : CliCommand {
        override val name = "adapter-enable"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String = withAdapter(args) { adapterId, _ ->
            registry.transitionTo(adapterId, AdapterState.READY)
            "OK"
        }
    }

    private inner class DisableCommand : CliCommand {
        override val name = "adapter-disable"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String = withAdapter(args) { adapterId, _ ->
            registry.transitionTo(adapterId, AdapterState.DEGRADED)
            "OK"
        }
    }

    private inner class RestartCommand : CliCommand {
        override val name = "adapter-restart"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String = withAdapter(args) { adapterId, adapter ->
            registry.stop(adapterId)
            registry.unregister(adapterId)
            registry.register(adapterId, adapter)
            "OK"
        }
    }

    private inner class TestCommand : CliCommand {
        override val name = "adapter-test"
        override fun execute(args: List<String>): String = withAdapter(args) { _, adapter ->
            "healthy=${adapter.health().healthy}"
        }
    }

    private inner class DeleteCommand : CliCommand {
        override val name = "adapter-delete"
        override val isWriteOperation = true
        override fun execute(args: List<String>): String = withAdapter(args) { adapterId, _ ->
            registry.stop(adapterId)
            registry.unregister(adapterId)
            managedAdapters.remove(adapterId)
            "OK"
        }
    }

    private inner class AddCommand : CliCommand {
        override val name = "adapter-add"
        override fun execute(args: List<String>): String =
            "Dodawanie adaptera przez CLI nie jest obsługiwane w tej fazie (SPEC-0024, §Otwarte decyzje, poz. 9) - " +
                "nowy adapter wymaga zmiany konfiguracji punktu kompozycji i restartu procesu."
    }
}
