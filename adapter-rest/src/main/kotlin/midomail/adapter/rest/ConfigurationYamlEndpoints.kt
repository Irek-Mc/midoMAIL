package midomail.adapter.rest

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import midomail.adapter.rest.dto.toDto
import midomail.domain.configuration.ConfigurationValidator
import midomail.domain.port.ConfigurationProvider

/**
 * Endpointy pełnej konfiguracji YAML (SPEC-0025, ADR-0032, 65-Konfiguracja.md §3) — całość
 * dokumentu przechowywana jako jedna wartość pod ustalonym kluczem [FULL_CONFIGURATION_KEY] w
 * [ConfigurationProvider] (Faza 5), reużywając jego historię/rollback na poziomie CAŁEGO
 * dokumentu.
 *
 * Ścieżki pod prefiksem `/config/yaml/` — `POST /config`/`POST /config/rollback` (wąski zapis per
 * klucz, Iteracja 5.12/`ConfigurationEndpoints`) już zajmują `/config`/`/config/rollback`; różny
 * prefiks unika kolizji tras w `AdminHttpServer` (mapa tras nadpisałaby cicho, gdyby ścieżki się
 * pokrywały — znalezione podczas pisania tej iteracji, przed napisaniem testów).
 *
 * **Świadomie wyłącznie REST, bez lustra CLI** (Iteracja 6.14) — `:adapter-cli` celowo nie ma
 * `kaml`/`kotlinx.serialization` (ADR-0025, minimalizacja zależności modułu CLI); dodanie ich
 * wyłącznie dla tej jednej kategorii zdublowałoby zależność w module utrzymywanym świadomie
 * lekkim. Administratorzy CLI/powłoki zarządzają plikami YAML przez REST bezpośrednio (np.
 * `curl --data-binary @config.yaml`), naturalny sposób pracy z plikami z linii poleceń.
 */
class ConfigurationYamlEndpoints(
    private val configurationProvider: ConfigurationProvider,
    private val codec: YamlConfigurationCodec,
    private val validator: ConfigurationValidator,
    private val platformProfile: String
) {
    private val json = Json

    fun registerRoutes(server: AdminHttpServer) {
        server.route("GET", "/config/yaml/export") { exchange ->
            val current = configurationProvider.getValue(FULL_CONFIGURATION_KEY)
            if (current == null) {
                AdminHttpServer.respond(exchange, 404, "Brak zapisanej konfiguracji")
                return@route
            }
            AdminHttpServer.respond(exchange, 200, current)
        }

        server.route("POST", "/config/yaml/validate") { exchange ->
            val yamlText = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val document = try {
                codec.decode(yamlText)
            } catch (exception: Exception) {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowy YAML: ${exception.message}")
                return@route
            }
            val result = validator.validate(document, platformProfile)
            AdminHttpServer.respond(exchange, 200, json.encodeToString(result.toDto()))
        }

        server.route("POST", "/config/yaml/import") { exchange ->
            val yamlText = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val document = try {
                codec.decode(yamlText)
            } catch (exception: Exception) {
                AdminHttpServer.respond(exchange, 400, "Nieprawidłowy YAML: ${exception.message}")
                return@route
            }
            val result = validator.validate(document, platformProfile)
            if (!result.isValid) {
                AdminHttpServer.respond(exchange, 422, json.encodeToString(result.toDto()))
                return@route
            }
            configurationProvider.setValue(FULL_CONFIGURATION_KEY, yamlText)
            AdminHttpServer.respond(exchange, 200, "OK")
        }

        server.route("GET", "/config/yaml/history") { exchange ->
            AdminHttpServer.respond(exchange, 200, json.encodeToString(configurationProvider.history(FULL_CONFIGURATION_KEY)))
        }

        server.route("POST", "/config/yaml/rollback") { exchange ->
            val restored = configurationProvider.rollback(FULL_CONFIGURATION_KEY)
            if (restored == null) {
                AdminHttpServer.respond(exchange, 409, "Brak historii do przywrócenia")
                return@route
            }
            AdminHttpServer.respond(exchange, 200, "OK")
        }
    }

    companion object {
        const val FULL_CONFIGURATION_KEY = "gateway.fullConfiguration"
    }
}
