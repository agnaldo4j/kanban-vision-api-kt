package com.kanbanvision.domain.model.kanban

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KanbanErrorBehaviorTest {
    @Test
    fun `given each kanban error variant when created then payload is preserved`() {
        val board = KanbanError.BoardNotFound(id = "b-1")
        val card = KanbanError.CardNotFound(id = "c-1")
        val step = KanbanError.StepNotFound(id = "s-1")
        val org = KanbanError.OrganizationNotFound(id = "o-1")

        assertIs<KanbanError.BoardNotFound>(board)
        assertEquals("b-1", board.id)
        assertEquals("c-1", card.id)
        assertEquals("s-1", step.id)
        assertEquals("o-1", org.id)
    }
}
