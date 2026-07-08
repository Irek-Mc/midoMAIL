# ADR-0030 — Model RBAC (role, uprawnienia, konta)

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/70-Uzytkownicy-i-uprawnienia.md` wymaga: ról (Administrator/Operator/Audytor/Integrator + role niestandardowe — §2), uprawnień nadawanych per obszar UI (§3: Dashboard, Komunikaty, Routing, Adaptery, Konfiguracja, Monitoring, Diagnostyka, Logi, Statystyki), zarządzania kontami (tworzenie/blokowanie, przypisywanie ról, reset poświadczeń, historia logowania, audyt zmian uprawnień — §4). §5 zastrzega: model wieloużytkownikowy jest dla wdrożeń serwerowych (JVM/Linux); Android używa uproszczonego modelu jednego administratora (już zbudowanego — `SecretStoreAdminAuthenticator`, Faza 5).

**Decyzja z Kontekstu Fazy 6 (potwierdzona przez AskUserQuestion):** pełny model domenowy budowany i weryfikowany harnessem, bez rzeczywistego wdrożenia serwerowego — dojrzewa produkcyjnie w Fazie 7 razem z `:platform-jvm`.

## Decyzja

**„Rola" jest otwartym typem danych, nie zamkniętym wyliczeniem** — 70-Uzytkownicy-i-uprawnienia.md §2 explicite wymienia „Role niestandardowe" obok 4 nazwanych, więc Administrator/Operator/Audytor/Integrator są KONWENCJONALNYMI, wstępnie zdefiniowanymi nazwami (dane zasiewane przez punkt kompozycji), nie architektonicznie uprzywilejowanymi wartościami wymuszanymi przez model domenowy.

```kotlin
package midomail.domain.rbac

enum class AdministeredArea { DASHBOARD, MESSAGES, ROUTING, ADAPTERS, CONFIGURATION, MONITORING, DIAGNOSTICS, LOGS, STATISTICS, USERS }
enum class AccessLevel { READ, WRITE }

data class Permission(val area: AdministeredArea, val level: AccessLevel)

@JvmInline value class RoleId(val value: String)

data class Role(val roleId: RoleId, val name: String, val permissions: Set<Permission>) {
    /** WRITE domyślnie obejmuje READ (kto może zapisać, może też odczytać) — konwencja, nie osobne uprawnienie do nadania. */
    fun grants(area: AdministeredArea, level: AccessLevel): Boolean =
        permissions.any { it.area == area && (it.level == level || (it.level == AccessLevel.WRITE && level == AccessLevel.READ)) }
}

@JvmInline value class AccountId(val value: String)
enum class AccountStatus { ACTIVE, BLOCKED }

data class Account(
    val accountId: AccountId,
    val username: String,
    val roleId: RoleId,
    val status: AccountStatus = AccountStatus.ACTIVE
)

interface RoleStore {
    fun findById(roleId: RoleId): Role?
    fun all(): List<Role>
    fun save(role: Role)
}

interface AccountStore {
    fun findByUsername(username: String): Account?
    fun findById(accountId: AccountId): Account?
    fun all(): List<Account>
    fun create(account: Account, passwordHash: String)
    fun updateStatus(accountId: AccountId, status: AccountStatus)
    fun updateRole(accountId: AccountId, roleId: RoleId)
    fun resetPassword(accountId: AccountId, newPasswordHash: String)
}

sealed class AuthenticationResult {
    data class Success(val account: Account) : AuthenticationResult()
    data object InvalidCredentials : AuthenticationResult()
    data object AccountBlocked : AuthenticationResult()
}

class AccountAuthenticator(private val accountStore: AccountStore) {
    fun authenticate(username: String, password: String): AuthenticationResult
}
```

**`AdministeredArea.USERS`** dodane, mimo że 70-Uzytkownicy-i-uprawnienia.md §3 nie wymienia „Użytkownicy i uprawnienia" wśród obszarów, do których nadaje się uprawnienia — bez tego zarządzanie kontami byłoby nieograniczone przez sam model RBAC, co jest sprzeczne z zasadą najmniejszych uprawnień (§1 tego dokumentu). Świadome, udokumentowane rozszerzenie względem litery dokumentu, nie milcząca improwizacja.

**Hashowanie haseł: SHA-256 bez soli, jawnie odnotowane jako uproszczenie tej fazy.** Prawdziwe hashowanie produkcyjne (bcrypt/Argon2/PBKDF2 z solą per-konto, faktor pracy) jest odkładane do Fazy 7 razem z rzeczywistym wdrożeniem serwerowym — ten model RBAC istnieje w tej fazie wyłącznie do zweryfikowania harnessem, nie do produkcyjnego wystawienia. Porównanie hasha stało-czasowe (`MessageDigest.isEqual`), tym samym wzorcem co `SecretStoreAdminAuthenticator` (Faza 5).

**„Historia logowania"/„Audyt zmian uprawnień"** (§4) — realizowane na warstwie Admin API (Iteracja 6.15) przez istniejący `AdminAuditRecorder`/`EventCategory.ADMINISTRATIVE` (Faza 5), nie nowy mechanizm — ten sam duch co audyt zmian konfiguracji/routingu.

## Konsekwencje

- Model w pełni niezależny od `SecretStoreAdminAuthenticator` (Faza 5) — Android nadal używa statycznego klucza, nie kont; oba mechanizmy uwierzytelniania będą musiały współistnieć w `AdminHttpServer` (rozstrzygane w Iteracji 6.15).
- `RoleStore`/`AccountStore` to nowe porty w `:domain`, implementacje in-memory na wzór `InMemoryConfigurationProvider` (Faza 5) — trwałość między restartami procesu poza zakresem (ten sam profil ograniczenia co inne mechanizmy „w pamięci" tego projektu).
- Hashowanie SHA-256 bez soli jest jawnym długiem architektonicznym (nie luką ukrytą) — odnotowane w Architectural Debt Report Fazy 6 (Iteracja 6.30) i ponownie przy zamknięciu Fazy 7.
- `Role.grants()` jest jedynym miejscem logiki autoryzacji — Admin API (Iteracja 6.15) wywołuje ją przed każdą operacją wymagającą konta, nie duplikuje logiki sprawdzania uprawnień.

## Dokumenty powiązane

- 60-User-Interface/70-Uzytkownicy-i-uprawnienia.md
- 90-ADR/ADR-0019-Uwierzytelnianie-API-Administracyjnego.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
