package com.kanbanvision.domain.model.valueobjects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkItemIdTest {
    @Test
    fun `generate returns non-blank id`() {
        val id = WorkItemId.generate()
        assertTrue(id.value.isNotBlank())
    }

    @Test
    fun `generate returns unique ids`() {
        val id1 = WorkItemId.generate()
        val id2 = WorkItemId.generate()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `constructor with valid value succeeds`() {
        val id = WorkItemId("item-1")
        assertEquals("item-1", id.value)
    }

    @Test
    fun `constructor with blank value throws`() {
        assertFailsWith<IllegalArgumentException> { WorkItemId("") }
    }

    @Test
    fun `constructor with whitespace-only value throws`() {
        assertFailsWith<IllegalArgumentException> { WorkItemId("   ") }
    }
}
