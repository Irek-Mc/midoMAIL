package midomail.domain.rbac

/**
 * Obszary UI, do których nadaje się uprawnienia (ADR-0030-RBAC-Model.md, 70-Uzytkownicy-i-uprawnienia.md
 * §3). [USERS] dodane mimo braku wprost w §3 — bez niego zarządzanie kontami byłoby nieograniczone
 * przez sam model RBAC (świadome, udokumentowane rozszerzenie, patrz ADR-0030).
 */
enum class AdministeredArea {
    DASHBOARD, MESSAGES, ROUTING, ADAPTERS, CONFIGURATION, MONITORING, DIAGNOSTICS, LOGS, STATISTICS, USERS
}

enum class AccessLevel { READ, WRITE }

data class Permission(val area: AdministeredArea, val level: AccessLevel)

@JvmInline
value class RoleId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoleId nie może być pusty" }
    }
}

/**
 * Rola (ADR-0030-RBAC-Model.md, 70-Uzytkownicy-i-uprawnienia.md §2) — otwarty typ danych, nie
 * zamknięte wyliczenie. Administrator/Operator/Audytor/Integrator to konwencjonalne nazwy zasiewane
 * przez punkt kompozycji, nie wartości uprzywilejowane w tym modelu.
 */
data class Role(val roleId: RoleId, val name: String, val permissions: Set<Permission>) {

    /**
     * `WRITE` domyślnie obejmuje `READ` (kto może zapisać, może też odczytać) — konwencja, nie
     * osobne uprawnienie do nadania.
     */
    fun grants(area: AdministeredArea, level: AccessLevel): Boolean =
        permissions.any { it.area == area && (it.level == level || (it.level == AccessLevel.WRITE && level == AccessLevel.READ)) }
}

/** Port repozytorium ról (ADR-0030). */
interface RoleStore {
    fun findById(roleId: RoleId): Role?
    fun all(): List<Role>
    fun save(role: Role)
}
