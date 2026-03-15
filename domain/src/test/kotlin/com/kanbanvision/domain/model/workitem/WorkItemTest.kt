package com.kanbanvision.domain.model.workitem

import com.kanbanvision.domain.model.valueobjects.WorkItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkItemTest {
    @Test
    fun `create returns work item with default STANDARD service class and TODO state`() {
        val item = WorkItem.create("Implement feature")
        assertEquals("Implement feature", item.title)
        assertEquals(ServiceClass.STANDARD, item.serviceClass)
        assertEquals(WorkItemState.TODO, item.state)
        assertEquals(0, item.agingDays)
        assertTrue(item.id.value.isNotBlank())
    }

    @Test
    fun `create with explicit service class`() {
        val item = WorkItem.create("Urgent fix", ServiceClass.EXPEDITE)
        assertEquals(ServiceClass.EXPEDITE, item.serviceClass)
    }

    @Test
    fun `create with blank title throws`() {
        assertFailsWith<IllegalArgumentException> { WorkItem.create("") }
    }

    @Test
    fun `advance from TODO transitions to IN_PROGRESS`() {
        val item = WorkItem.create("Task")
        val advanced = item.advance()
        assertEquals(WorkItemState.IN_PROGRESS, advanced.state)
    }

    @Test
    fun `advance from IN_PROGRESS transitions to DONE`() {
        val item = WorkItem.create("Task").advance()
        val done = item.advance()
        assertEquals(WorkItemState.DONE, done.state)
    }

    @Test
    fun `advance from BLOCKED transitions to IN_PROGRESS`() {
        val item = WorkItem.create("Task").advance().block()
        val unblocked = item.advance()
        assertEquals(WorkItemState.IN_PROGRESS, unblocked.state)
    }

    @Test
    fun `advance from DONE returns same item`() {
        val item = WorkItem.create("Task").advance().advance()
        assertEquals(item, item.advance())
    }

    @Test
    fun `block transitions IN_PROGRESS to BLOCKED`() {
        val item = WorkItem.create("Task").advance()
        val blocked = item.block()
        assertEquals(WorkItemState.BLOCKED, blocked.state)
    }

    @Test
    fun `block on non-IN_PROGRESS item throws`() {
        val item = WorkItem.create("Task")
        assertFailsWith<IllegalArgumentException> { item.block() }
    }

    @Test
    fun `incrementAge increases agingDays by one`() {
        val item = WorkItem.create("Task")
        val aged = item.incrementAge()
        assertEquals(1, aged.agingDays)
    }

    @Test
    fun `negative agingDays throws on construction`() {
        assertFailsWith<IllegalArgumentException> {
            WorkItem(
                id = WorkItemId.generate(),
                title = "Task",
                serviceClass = ServiceClass.STANDARD,
                state = WorkItemState.TODO,
                agingDays = -1,
            )
        }
    }
}
