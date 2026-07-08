package midomail.domain.rbac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryRoleStoreTest {

    @Test
    fun `save then findById returns the saved role`() {
        val store = InMemoryRoleStore()
        val role = Role(RoleId("administrator"), "Administrator", setOf(Permission(AdministeredArea.USERS, AccessLevel.WRITE)))

        store.save(role)

        assertEquals(role, store.findById(RoleId("administrator")))
    }

    @Test
    fun `findById returns null for an unknown role`() {
        val store = InMemoryRoleStore()

        assertNull(store.findById(RoleId("unknown")))
    }

    @Test
    fun `save overwrites an existing role with the same RoleId`() {
        val store = InMemoryRoleStore()
        store.save(Role(RoleId("operator"), "Operator", emptySet()))

        store.save(Role(RoleId("operator"), "Operator", setOf(Permission(AdministeredArea.ADAPTERS, AccessLevel.WRITE))))

        assertEquals(1, store.all().size)
        assertTrue(store.findById(RoleId("operator"))!!.permissions.isNotEmpty())
    }

    @Test
    fun `all returns every saved role`() {
        val store = InMemoryRoleStore()
        store.save(Role(RoleId("administrator"), "Administrator", emptySet()))
        store.save(Role(RoleId("audytor"), "Audytor", emptySet()))

        assertEquals(2, store.all().size)
    }
}
