package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterState
import midomail.domain.adapter.Registry
import midomail.domain.administration.ManagedAdapters

/**
 * Endpointy zarządzania adapterami (SPEC-0024-Administrative-API-Contract.md, §2 Zarządzanie
 * adapterami — 64-Adaptery.md §4).
 *
 * Mapowanie operacji na model cyklu życia (SPEC-0014):
 * - **Włącz/Wyłącz** — `Registry.transitionTo(READY)`/`transitionTo(DEGRADED)`. `DEGRADED` jest
 *   jedynym stanem nieterminalnym, dwukierunkowo powiązanym z `READY` (`STOPPED` jest terminalny —
 *   `transitionTo` z niego nigdy się nie powiedzie, restart wymaga pełnego cyklu poniżej).
 * - **Restart** — pełny cykl `stop()` → `unregister()` → `register()` na TEJ SAMEJ instancji
 *   adaptera (z [ManagedAdapters]) — faktycznie wywołuje `adapter.stop()`/`start()` ponownie.
 * - **Test połączenia** — `Adapter.health()` na żądanie, bez efektów ubocznych.
 * - **Usuń** — `stop()` → `unregister()` → usunięcie z [ManagedAdapters].
 * - **Dodaj adapter** — ŚWIADOMIE POZA ZAKRESEM tej fazy (SPEC-0024, §Otwarte decyzje, poz. 9):
 *   SPEC-0010 opisuje wyłącznie rejestrację sterowaną przez punkt kompozycji (`AdapterFactory`),
 *   nie dynamiczne tworzenie z API. Endpoint istnieje i jawnie zwraca 501, żeby wywołujący od razu
 *   wiedział, że operacja nie jest obsługiwana — nie ciche 404 sugerujące literówkę w ścieżce.
 */
class AdapterManagementEndpoints(
    private val registry: Registry,
    private val managedAdapters: ManagedAdapters
) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("POST", "/adapters/enable") { exchange ->
            withAdapter(exchange) { adapterId, _ ->
                registry.transitionTo(adapterId, AdapterState.READY)
                AdminHttpServer.respond(exchange, 200, "OK")
            }
        }

        server.route("POST", "/adapters/disable") { exchange ->
            withAdapter(exchange) { adapterId, _ ->
                registry.transitionTo(adapterId, AdapterState.DEGRADED)
                AdminHttpServer.respond(exchange, 200, "OK")
            }
        }

        server.route("POST", "/adapters/restart") { exchange ->
            withAdapter(exchange) { adapterId, adapter ->
                registry.stop(adapterId)
                registry.unregister(adapterId)
                registry.register(adapterId, adapter)
                AdminHttpServer.respond(exchange, 200, "OK")
            }
        }

        server.route("POST", "/adapters/test") { exchange ->
            withAdapter(exchange) { _, adapter ->
                val health = adapter.health()
                AdminHttpServer.respond(exchange, 200, json.encodeToString(health.healthy))
            }
        }

        server.route("DELETE", "/adapters") { exchange ->
            withAdapter(exchange) { adapterId, _ ->
                registry.stop(adapterId)
                registry.unregister(adapterId)
                managedAdapters.remove(adapterId)
                AdminHttpServer.respond(exchange, 200, "OK")
            }
        }

        server.route("POST", "/adapters") { exchange ->
            AdminHttpServer.respond(
                exchange,
                501,
                "Dodawanie adaptera przez API administracyjne nie jest obsługiwane w tej fazie " +
                    "(SPEC-0024, §Otwarte decyzje, poz. 9) - nowy adapter wymaga zmiany konfiguracji " +
                    "punktu kompozycji i restartu procesu."
            )
        }
    }

    private fun withAdapter(exchange: com.sun.net.httpserver.HttpExchange, action: (AdapterId, midomail.domain.adapter.Adapter) -> Unit) {
        val id = queryParam(exchange, "id")
        if (id == null) {
            AdminHttpServer.respond(exchange, 400, "Wymagany parametr zapytania: id")
            return
        }
        val adapterId = AdapterId(id)
        val adapter = managedAdapters.get(adapterId)
        if (adapter == null) {
            AdminHttpServer.respond(exchange, 404, "Nieznany adapter: $id")
            return
        }
        try {
            action(adapterId, adapter)
        } catch (exception: Exception) {
            AdminHttpServer.respond(exchange, 409, exception.message ?: "Operacja nie powiodła się")
        }
    }

    private fun queryParam(exchange: com.sun.net.httpserver.HttpExchange, name: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
    }
}
