package midomail.adapter.rest.dto

import kotlinx.serialization.Serializable
import midomail.domain.rbac.Account
import midomail.domain.rbac.AccessLevel
import midomail.domain.rbac.AdministeredArea
import midomail.domain.rbac.Permission
import midomail.domain.rbac.Role

@Serializable
data class PermissionDto(val area: String, val level: String)

@Serializable
data class RoleDto(val roleId: String, val name: String, val permissions: List<PermissionDto>)

@Serializable
data class AccountDto(val accountId: String, val username: String, val roleId: String, val status: String)

@Serializable
data class LoginRequestDto(val username: String, val password: String)

@Serializable
data class LoginResultDto(val outcome: String, val account: AccountDto? = null)

fun Role.toDto(): RoleDto = RoleDto(
    roleId = roleId.value,
    name = name,
    permissions = permissions.map { PermissionDto(it.area.name, it.level.name) }
)

fun RoleDto.toDomain(): Role = Role(
    roleId = midomail.domain.rbac.RoleId(roleId),
    name = name,
    permissions = permissions.map { Permission(AdministeredArea.valueOf(it.area), AccessLevel.valueOf(it.level)) }.toSet()
)

fun Account.toDto(): AccountDto = AccountDto(accountId.value, username, roleId.value, status.name)
