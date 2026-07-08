package midomail.domain.rbac

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Potwierdza `Role.grants()` (ADR-0030-RBAC-Model.md).
 */
class RoleTest {

    @Test
    fun `a role with READ permission grants READ but not WRITE`() {
        val role = Role(RoleId("audytor"), "Audytor", setOf(Permission(AdministeredArea.ROUTING, AccessLevel.READ)))

        assertTrue(role.grants(AdministeredArea.ROUTING, AccessLevel.READ))
        assertFalse(role.grants(AdministeredArea.ROUTING, AccessLevel.WRITE))
    }

    @Test
    fun `a role with WRITE permission also grants READ for the same area`() {
        val role = Role(RoleId("operator"), "Operator", setOf(Permission(AdministeredArea.ROUTING, AccessLevel.WRITE)))

        assertTrue(role.grants(AdministeredArea.ROUTING, AccessLevel.WRITE))
        assertTrue(role.grants(AdministeredArea.ROUTING, AccessLevel.READ))
    }

    @Test
    fun `a role grants nothing for an area it was not given permission to`() {
        val role = Role(RoleId("audytor"), "Audytor", setOf(Permission(AdministeredArea.ROUTING, AccessLevel.READ)))

        assertFalse(role.grants(AdministeredArea.ADAPTERS, AccessLevel.READ))
    }

    @Test
    fun `a role with no permissions grants nothing`() {
        val role = Role(RoleId("empty"), "Bez uprawnień", emptySet())

        assertFalse(role.grants(AdministeredArea.DASHBOARD, AccessLevel.READ))
    }
}
