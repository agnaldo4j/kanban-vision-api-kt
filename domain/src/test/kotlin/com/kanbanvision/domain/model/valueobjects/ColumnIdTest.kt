package com.kanbanvision.domain.model.valueobjects

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ColumnIdTest {
    @Test
    fun `generate returns non-blank uuid`() {
        val id = ColumnId.generate()
        assertTrue(id.value.isNotBlank())
    }

    @Test
    fun `generate returns unique ids on each call`() {
        val id1 = ColumnId.generate()
        val id2 = ColumnId.generate()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `constructor accepts valid value`() {
        val id = ColumnId("abc-123")
        assertTrue(id.value == "abc-123")
    }

    @Test
    fun `constructor with blank value throws`() {
        assertThrows<IllegalArgumentException> { ColumnId("") }
    }

    @Test
    fun `constructor with whitespace-only value throws`() {
        assertThrows<IllegalArgumentException> { ColumnId("   ") }
    }
}
