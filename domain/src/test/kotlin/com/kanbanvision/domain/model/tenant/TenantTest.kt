package com.kanbanvision.domain.model.tenant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TenantTest {
    @Test
    fun `create returns tenant with generated id and given name`() {
        val tenant = Tenant.create("Acme Corp")
        assertEquals("Acme Corp", tenant.name)
        assertTrue(tenant.id.value.isNotBlank())
    }

    @Test
    fun `create with blank name throws`() {
        assertFailsWith<IllegalArgumentException> { Tenant.create("") }
    }

    @Test
    fun `create with whitespace-only name throws`() {
        assertFailsWith<IllegalArgumentException> { Tenant.create("   ") }
    }

    @Test
    fun `create generates unique ids`() {
        val t1 = Tenant.create("Corp A")
        val t2 = Tenant.create("Corp B")
        assertNotEquals(t1.id, t2.id)
    }
}
