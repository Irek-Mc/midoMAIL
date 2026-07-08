package midomail.domain.adapter

/**
 * Identyfikator zarejestrowanego adaptera (10-Core/14-Registry-Adapterow.md;
 * SPEC-0010-Plugin-SDK-Contract.md; ADR-0012-Channel-AdapterId.md).
 *
 * Współdzielony przez [midomail.domain.message.Channel] (wskazanie adaptera obsługującego
 * Source/Destination) oraz Registry Adapterów (Iteracja 8) — jedno pojęcie, nie duplikat.
 */
@JvmInline
value class AdapterId(val value: String) {
    init {
        require(value.isNotBlank()) { "AdapterId nie może być pusty" }
    }
}
