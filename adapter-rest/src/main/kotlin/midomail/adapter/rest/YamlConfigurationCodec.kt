package midomail.adapter.rest

import com.charleskorn.kaml.Yaml
import midomail.adapter.rest.dto.ConfigurationDocumentDto
import midomail.adapter.rest.dto.toDomain
import midomail.adapter.rest.dto.toDto
import midomail.domain.configuration.ConfigurationDocument

/**
 * Kodek YAML↔`ConfigurationDocument` (ADR-0032-Konfiguracja-YAML-Pelna.md) — `kaml`, zbudowany na
 * `kotlinx.serialization` (już zaakceptowanym wyjątku z Fazy 5). Wyłącznie tutaj, nigdy w
 * `:domain`.
 */
class YamlConfigurationCodec {
    private val yaml = Yaml.default

    fun encode(document: ConfigurationDocument): String = yaml.encodeToString(ConfigurationDocumentDto.serializer(), document.toDto())

    fun decode(yamlText: String): ConfigurationDocument = yaml.decodeFromString(ConfigurationDocumentDto.serializer(), yamlText).toDomain()
}
