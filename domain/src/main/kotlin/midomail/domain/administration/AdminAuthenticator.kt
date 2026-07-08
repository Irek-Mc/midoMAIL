package midomail.domain.administration

/**
 * Uwierzytelnianie API administracyjnego (ADR-0019-Uwierzytelnianie-API-Administracyjnego.md) —
 * statyczny klucz API, bez rozróżnienia ról (autoryzacja pozostaje Fazą 6).
 */
interface AdminAuthenticator {
    fun authenticate(providedKey: String): Boolean
}
