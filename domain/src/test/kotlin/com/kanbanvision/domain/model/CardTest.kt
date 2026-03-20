package com.kanbanvision.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardTest {
    @Test
    fun `create returns work item with default STANDARD service class and TODO state`() {
        val item = Card.createSimulation("Implement feature")
        assertEquals("Implement feature", item.title)
        assertEquals(ServiceClass.STANDARD, item.serviceClass)
        assertEquals(CardState.TODO, item.state)
        assertEquals(0, item.agingDays)
        assertTrue(item.id.isNotBlank())
    }

    @Test
    fun `create with explicit service class`() {
        val item = Card.createSimulation("Urgent fix", ServiceClass.EXPEDITE)
        assertEquals(ServiceClass.EXPEDITE, item.serviceClass)
    }

    @Test
    fun `create with blank title throws`() {
        assertFailsWith<IllegalArgumentException> { Card.createSimulation("") }
    }

    @Test
    fun `advance from TODO transitions to IN_PROGRESS`() {
        val item = Card.createSimulation("Task")
        val advanced = item.advance()
        assertEquals(CardState.IN_PROGRESS, advanced.state)
    }

    @Test
    fun `advance from IN_PROGRESS transitions to DONE`() {
        val item = Card.createSimulation("Task").advance()
        val done = item.advance()
        assertEquals(CardState.DONE, done.state)
    }

    @Test
    fun `advance from BLOCKED transitions to IN_PROGRESS`() {
        val item = Card.createSimulation("Task").advance().block()
        val unblocked = item.advance()
        assertEquals(CardState.IN_PROGRESS, unblocked.state)
    }

    @Test
    fun `advance from DONE returns same item`() {
        val item = Card.createSimulation("Task").advance().advance()
        assertEquals(item, item.advance())
    }

    @Test
    fun `block transitions IN_PROGRESS to BLOCKED`() {
        val item = Card.createSimulation("Task").advance()
        val blocked = item.block()
        assertEquals(CardState.BLOCKED, blocked.state)
    }

    @Test
    fun `block on non-IN_PROGRESS item throws`() {
        val item = Card.createSimulation("Task")
        assertFailsWith<IllegalArgumentException> { item.block() }
    }

    @Test
    fun `incrementAge increases agingDays by one`() {
        val item = Card.createSimulation("Task")
        val aged = item.incrementAge()
        assertEquals(1, aged.agingDays)
    }

    @Test
    fun `negative agingDays throws on construction`() {
        assertFailsWith<IllegalArgumentException> {
            Card(
                id = UUID.randomUUID().toString(),
                title = "Task",
                serviceClass = ServiceClass.STANDARD,
                state = CardState.TODO,
                agingDays = -1,
            )
        }
    }
}
