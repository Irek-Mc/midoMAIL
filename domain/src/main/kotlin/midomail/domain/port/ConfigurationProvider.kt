package midomail.domain.port

/**
 * Port konfiguracji (30-Infrastructure/30-Konfiguracja.md; SPEC-0012-Configuration-Provider-Contract.md;
 * zapis/historia/rollback dodane w ADR-0020-Konfiguracja-Zapis.md, Faza 5).
 *
 * [key] odpowiada ścieżce w hierarchii YAML (np. `gateway.instanceId`) — dokładny format ścieżki
 * jest doprecyzowany wraz z implementacją rzeczywistego parsera konfiguracji (poza zakresem Fazy 1
 * i nadal poza zakresem Fazy 5 — ADR-0020, §Świadomie POZA zakresem: brak pełnego parsera plików
 * YAML/importu-eksportu/walidacji krzyżowej, wyłącznie zapis par klucz-wartość w pamięci procesu).
 */
interface ConfigurationProvider {
    fun getValue(key: String): String?

    /** Zapisuje poprzednią wartość [key] (jeśli istniała) do historii przed nadpisaniem. */
    fun setValue(key: String, value: String)

    /** Poprzednie wartości [key], najnowsza pierwsza. Pusta lista, jeśli klucz nigdy nie był nadpisany. */
    fun history(key: String): List<String>

    /** Przywraca najnowszą wartość z historii jako bieżącą, zdejmując ją z historii. `null`, jeśli historia jest pusta. */
    fun rollback(key: String): String?
}
