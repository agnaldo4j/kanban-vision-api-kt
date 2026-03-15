package com.kanbanvision.domain.model.valueobjects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScenarioIdTest {
    @Test
    fun `generate returns non-blank id`() {
        val id = ScenarioId.generate()
        assertTrue(id.value.isNotBlank())
    }

    @Test
    fun `generate returns unique ids`() {
        val id1 = ScenarioId.generate()
        val id2 = ScenarioId.generate()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `constructor with valid value succeeds`() {
        val id = ScenarioId("scenario-1")
        assertEquals("scenario-1", id.value)
    }

    @Test
    fun `constructor with blank value throws`() {
        assertFailsWith<IllegalArgumentException> { ScenarioId("") }
    }

    @Test
    fun `constructor with whitespace-only value throws`() {
        assertFailsWith<IllegalArgumentException> { ScenarioId("   ") }
    }
}
