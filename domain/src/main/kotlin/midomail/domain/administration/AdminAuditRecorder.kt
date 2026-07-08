package midomail.domain.administration

/**
 * Audyt operacji administracyjnych (SPEC-0024-Administrative-API-Contract.md, §Uwierzytelnianie
 * i audyt; 60-User-Interface/70-Uzytkownicy-i-uprawnienia.md §6: „Wszystkie operacje
 * administracyjne są rejestrowane"). Reużywa `EventCategory.ADMINISTRATIVE` (istniejąca w
 * zamkniętej taksonomii `Event` od Fazy 1, SPEC-0003 — nigdy dotąd niewykorzystana), nie nowy
 * mechanizm.
 *
 * Zakres audytu (decyzja SPEC-0024, poz. 10): operacje ZAPISU i błędy uwierzytelnienia zawsze;
 * odczyty opcjonalnie (nie audytowane przez implementację referencyjną — [operation] wywoływane
 * wyłącznie z endpointów/komend zapisu).
 */
fun interface AdminAuditRecorder {
    /** [authenticated] `false` oznacza próbę operacji z nieprawidłowym/brakującym uwierzytelnieniem. */
    fun record(operation: String, authenticated: Boolean)
}
