package com.kanbanvision.domain.model.valueobjects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TenantIdTest {
    @Test
    fun `generate returns non-blank id`() {
        val id = TenantId.generate()
        assertTrue(id.value.isNotBlank())
    }

    @Test
    fun `generate returns unique ids`() {
        val id1 = TenantId.generate()
        val id2 = TenantId.generate()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `constructor with valid value succeeds`() {
        val id = TenantId("tenant-1")
        assertEquals("tenant-1", id.value)
    }

    @Test
    fun `constructor with blank value throws`() {
        assertFailsWith<IllegalArgumentException> { TenantId("") }
    }

    @Test
    fun `constructor with whitespace-only value throws`() {
        assertFailsWith<IllegalArgumentException> { TenantId("   ") }
    }
}
