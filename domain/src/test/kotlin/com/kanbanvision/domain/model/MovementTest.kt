package com.kanbanvision.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MovementTest {
    private val cardId = UUID.randomUUID().toString()

    @Test
    fun `movement holds all fields`() {
        val m = Movement(type = MovementType.MOVED, cardId = cardId, day = SimulationDay(3), reason = "decision: move")
        assertEquals(MovementType.MOVED, m.type)
        assertEquals(cardId, m.cardId)
        assertEquals(SimulationDay(3), m.day)
        assertEquals("decision: move", m.reason)
    }

    @Test
    fun `COMPLETED movement is distinct from MOVED`() {
        val moved = Movement(MovementType.MOVED, cardId, SimulationDay(1), "moved")
        val completed = Movement(MovementType.COMPLETED, cardId, SimulationDay(1), "completed")
        assertEquals(MovementType.COMPLETED, completed.type)
        assertNotEquals(moved.type, completed.type)
    }

    @Test
    fun `BLOCKED and UNBLOCKED are distinct types`() {
        assertNotEquals(MovementType.BLOCKED, MovementType.UNBLOCKED)
    }
}
