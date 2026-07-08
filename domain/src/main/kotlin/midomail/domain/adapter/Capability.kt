package midomail.domain.adapter

/**
 * Możliwości deklarowane przez adapter (SPEC-0010-Plugin-SDK-Contract.md, §Model Capability).
 * Nowa wartość nie wymaga zmiany interfejsu [Adapter] ani Gateway Engine — Routing Engine
 * sprawdza przynależność do zbioru generycznie (`Set.contains`), nigdy nie odczytuje wartości
 * przez rzutowanie na konkretny typ transportu.
 */
enum class Capability {
    SUPPORTS_ATTACHMENTS,
    SUPPORTS_MMS,
    SUPPORTS_MULTIPART
}
