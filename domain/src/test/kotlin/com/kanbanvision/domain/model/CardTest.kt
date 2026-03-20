package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardTest {
    private val columnId = UUID.randomUUID().toString()

    @Test
    fun `create card with valid data`() {
        val card = Card.create(columnId = columnId, title = "Task 1", position = 0)
        assertEquals("Task 1", card.title)
        assertEquals("", card.description)
        assertEquals(columnId, card.columnId)
        assertEquals(0, card.position)
        assertTrue(card.id.isNotBlank())
    }

    @Test
    fun `create card with custom description`() {
        val card = Card.create(columnId = columnId, title = "Task", description = "Details", position = 1)
        assertEquals("Details", card.description)
        assertEquals(1, card.position)
    }

    @Test
    fun `create card generates unique id on each call`() {
        val card1 = Card.create(columnId = columnId, title = "T1", position = 0)
        val card2 = Card.create(columnId = columnId, title = "T2", position = 1)
        assertNotEquals(card1.id, card2.id)
    }

    @Test
    fun `create card with blank title throws`() {
        assertThrows<IllegalArgumentException> { Card.create(columnId = columnId, title = "", position = 0) }
    }

    @Test
    fun `create card with whitespace-only title throws`() {
        assertThrows<IllegalArgumentException> { Card.create(columnId = columnId, title = "   ", position = 0) }
    }

    @Test
    fun `create card with blank column id throws`() {
        assertThrows<IllegalArgumentException> { Card.create(columnId = "", title = "Task", position = 0) }
    }

    @Test
    fun `card constructor with negative position throws`() {
        assertThrows<IllegalArgumentException> {
            Card(
                id = UUID.randomUUID().toString(),
                columnId = columnId,
                title = "Task",
                position = -1,
            )
        }
    }

    @Test
    fun `moveTo updates column and position`() {
        val card = Card.create(columnId = columnId, title = "Task", position = 0)
        val targetColumnId = UUID.randomUUID().toString()

        val moved = card.moveTo(targetColumnId, 3)

        assertEquals(targetColumnId, moved.columnId)
        assertEquals(3, moved.position)
        assertEquals(card.id, moved.id)
        assertEquals(card.title, moved.title)
    }

    @Test
    fun `moveTo does not mutate original card`() {
        val card = Card.create(columnId = columnId, title = "Task", position = 0)
        val targetColumnId = UUID.randomUUID().toString()

        card.moveTo(targetColumnId, 3)

        assertEquals(columnId, card.columnId)
        assertEquals(0, card.position)
    }

    @Test
    fun `moveTo with blank target column throws`() {
        val card = Card.create(columnId = columnId, title = "Task", position = 0)
        assertThrows<IllegalArgumentException> { card.moveTo("", 1) }
    }

    @Test
    fun `moveTo with negative target position throws`() {
        val card = Card.create(columnId = columnId, title = "Task", position = 0)
        val targetColumnId = UUID.randomUUID().toString()
        assertThrows<IllegalArgumentException> { card.moveTo(targetColumnId, -1) }
    }
}
