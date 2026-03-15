package com.kanbanvision.domain.model.movement

import com.kanbanvision.domain.model.valueobjects.WorkItemId
import kotlin.test.Test
import kotlin.test.assertEquals

class MovementTest {
    private val itemId = WorkItemId.generate()

    @Test
    fun `movement holds all fields`() {
        val m = Movement(type = MovementType.MOVED, workItemId = itemId, day = 3, reason = "decision: move")
        assertEquals(MovementType.MOVED, m.type)
        assertEquals(itemId, m.workItemId)
        assertEquals(3, m.day)
        assertEquals("decision: move", m.reason)
    }

    @Test
    fun `COMPLETED movement is distinct from MOVED`() {
        val moved = Movement(MovementType.MOVED, itemId, 1, "moved")
        val completed = Movement(MovementType.COMPLETED, itemId, 1, "completed")
        assertEquals(MovementType.COMPLETED, completed.type)
        assert(moved.type != completed.type)
    }

    @Test
    fun `BLOCKED and UNBLOCKED are distinct types`() {
        assert(MovementType.BLOCKED != MovementType.UNBLOCKED)
    }
}
