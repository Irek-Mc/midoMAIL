package midomail.domain.administration

import midomail.domain.adapter.AdapterId
import midomail.domain.port.ConfigurationProvider

/**
 * Typowana konfiguracja adaptera (ADR-0031-Adapter-Typed-Configuration.md) — zbudowana na
 * [ConfigurationProvider] (Faza 5), nie go zastępująca. Klucze mają konwencjonalny format
 * `adapters.{adapterId}.{pole}`.
 */
class AdapterConfigurationAdministration(private val configurationProvider: ConfigurationProvider) {

    /** Pola sekretne (`AdapterConfigurationSchema.SECRET_FIELDS`) zawsze zwracają `null`. */
    fun read(adapterId: AdapterId, adapterType: String): Map<String, String?> =
        AdapterConfigurationSchema.fieldsFor(adapterType).associateWith { field ->
            if (field in AdapterConfigurationSchema.SECRET_FIELDS) {
                null
            } else {
                configurationProvider.getValue(keyFor(adapterId, field))
            }
        }

    fun write(adapterId: AdapterId, adapterType: String, field: String, value: String) {
        require(field in AdapterConfigurationSchema.fieldsFor(adapterType)) {
            "Pole '$field' nie należy do schematu typu adaptera '$adapterType'"
        }
        configurationProvider.setValue(keyFor(adapterId, field), value)
    }

    private fun keyFor(adapterId: AdapterId, field: String): String = "adapters.${adapterId.value}.$field"
}
