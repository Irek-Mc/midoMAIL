package midomail.domain.rbac

import java.util.concurrent.ConcurrentHashMap

/** Implementacja referencyjna [RoleStore] w pamięci (ADR-0030) — wzorem `InMemoryConfigurationProvider`. */
class InMemoryRoleStore : RoleStore {

    private val roles = ConcurrentHashMap<RoleId, Role>()

    override fun findById(roleId: RoleId): Role? = roles[roleId]

    override fun all(): List<Role> = roles.values.toList()

    override fun save(role: Role) {
        roles[role.roleId] = role
    }
}
