package com.kanbanvision.persistence.internal.tables

import org.postgresql.util.PGobject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonbColumnTypeTest {
    private val columnType = JsonbColumnType()

    @Test
    fun `sqlType returns JSONB`() {
        assertEquals("JSONB", columnType.sqlType())
    }

    @Test
    fun `valueFromDB with PGobject returns its string value`() {
        val pgObject =
            PGobject().apply {
                type = "jsonb"
                value = """{"key":"value"}"""
            }
        assertEquals("""{"key":"value"}""", columnType.valueFromDB(pgObject))
    }

    @Test
    fun `valueFromDB with PGobject null value throws error`() {
        val pgObject =
            PGobject().apply {
                type = "jsonb"
                value = null
            }
        assertFailsWith<IllegalStateException> { columnType.valueFromDB(pgObject) }
    }

    @Test
    fun `valueFromDB with String returns the string directly`() {
        assertEquals("""{"a":1}""", columnType.valueFromDB("""{"a":1}"""))
    }

    @Test
    fun `valueFromDB with unexpected type throws error`() {
        assertFailsWith<IllegalStateException> { columnType.valueFromDB(42) }
    }

    @Test
    fun `notNullValueToDB wraps string as PGobject with jsonb type`() {
        val result = columnType.notNullValueToDB("""{"x":true}""")
        assertTrue(result is PGobject)
        assertEquals("jsonb", result.type)
        assertEquals("""{"x":true}""", result.value)
    }
}
