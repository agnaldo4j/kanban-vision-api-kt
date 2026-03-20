package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardTest {
    @Test
    fun `create board with valid name`() {
        val board = Board.create("My Board")
        assertEquals("My Board", board.name)
        assertTrue(board.id.isNotBlank())
        assertTrue(board.steps.isEmpty())
        assertNotNull(board.createdAt)
    }

    @Test
    fun `create board generates unique id on each call`() {
        val board1 = Board.create("Board 1")
        val board2 = Board.create("Board 2")
        assertNotEquals(board1.id, board2.id)
    }

    @Test
    fun `create board with blank name throws`() {
        assertThrows<IllegalArgumentException> { Board.create("") }
    }

    @Test
    fun `create board with whitespace-only name throws`() {
        assertThrows<IllegalArgumentException> { Board.create("   ") }
    }

    @Test
    fun `addStep returns step with correct boardId and auto position`() {
        val board = Board.create("My Board")
        val step = board.addStep("To Do", AbilityName.PRODUCT_MANAGER)
        assertEquals(board.id, step.boardId)
        assertEquals("To Do", step.name)
        assertEquals(0, step.position)
        assertEquals(AbilityName.PRODUCT_MANAGER, step.requiredAbility)
    }

    @Test
    fun `addStep position equals existing steps count`() {
        val boardId = UUID.randomUUID().toString()
        val existing =
            Step.create(
                boardId = boardId,
                name = "To Do",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val board = Board(id = boardId, name = "My Board", steps = listOf(existing))
        val step = board.addStep("In Progress", AbilityName.DEVELOPER)
        assertEquals(1, step.position)
    }

    @Test
    fun `addStep with blank name throws`() {
        val board = Board.create("My Board")
        assertThrows<IllegalArgumentException> { board.addStep("", AbilityName.PRODUCT_MANAGER) }
    }

    @Test
    fun `addStep with duplicate name throws`() {
        val boardId = UUID.randomUUID().toString()
        val existing =
            Step.create(
                boardId = boardId,
                name = "To Do",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val board = Board(id = boardId, name = "My Board", steps = listOf(existing))
        assertThrows<IllegalArgumentException> { board.addStep("To Do", AbilityName.PRODUCT_MANAGER) }
    }

    @Test
    fun `addCard returns card with correct stepId and auto position`() {
        val boardId = UUID.randomUUID().toString()
        val stepId = UUID.randomUUID().toString()
        val step =
            Step(
                id = stepId,
                boardId = boardId,
                name = "To Do",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val board = Board(id = boardId, name = "My Board", steps = listOf(step))
        val card = board.addCard(step, "Fix bug")
        assertEquals(stepId, card.stepId)
        assertEquals("Fix bug", card.title)
        assertEquals(0, card.position)
    }

    @Test
    fun `addCard position equals existing cards count`() {
        val boardId = UUID.randomUUID().toString()
        val stepId = UUID.randomUUID().toString()
        val existingCard = Card.create(stepId = stepId, title = "Existing", position = 0)
        val step =
            Step(
                id = stepId,
                boardId = boardId,
                name = "To Do",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
                cards = listOf(existingCard),
            )
        val board = Board(id = boardId, name = "My Board", steps = listOf(step))
        val card = board.addCard(step, "New Card")
        assertEquals(1, card.position)
    }

    @Test
    fun `addCard throws when step does not belong to board`() {
        val boardId = UUID.randomUUID().toString()
        val otherBoardId = UUID.randomUUID().toString()
        val step =
            Step(
                id = UUID.randomUUID().toString(),
                boardId = otherBoardId,
                name = "To Do",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val board = Board(id = boardId, name = "My Board")
        assertThrows<IllegalArgumentException> { board.addCard(step, "Task") }
    }
}
