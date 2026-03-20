package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.team.Ability
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.team.Seniority
import com.kanbanvision.domain.model.team.Worker
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepTest {
    @Test
    fun `step stores all fields`() {
        val boardId = BoardId.generate()
        val stepId = ColumnId.generate()
        val step =
            Step(
                id = stepId,
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            )

        assertEquals(stepId, step.id)
        assertEquals(boardId, step.boardId)
        assertEquals("Development", step.name)
        assertEquals(1, step.position)
        assertEquals(AbilityName.DEVELOPER, step.requiredAbility)
        assertTrue(step.cards.isEmpty())
    }

    @Test
    fun `step copy keeps immutability semantics`() {
        val step =
            Step(
                id = ColumnId.generate(),
                boardId = BoardId.generate(),
                name = "Analysis",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val moved = step.copy(position = 2, name = "Done")

        assertEquals("Analysis", step.name)
        assertEquals(0, step.position)
        assertEquals("Done", moved.name)
        assertEquals(2, moved.position)
        assertEquals(step.id, moved.id)
        assertEquals(step.requiredAbility, moved.requiredAbility)
    }

    @Test
    fun `create step validates name and position`() {
        val boardId = BoardId.generate()

        assertThrows<IllegalArgumentException> {
            Step.create(
                boardId = boardId,
                name = " ",
                position = 0,
                requiredAbility = AbilityName.DEVELOPER,
            )
        }
        assertThrows<IllegalArgumentException> {
            Step.create(
                boardId = boardId,
                name = "Dev",
                position = -1,
                requiredAbility = AbilityName.DEVELOPER,
            )
        }
    }

    @Test
    fun `step assignment enforces required ability and single-step per worker`() {
        val boardId = BoardId.generate()
        val analysis = step(boardId, "Analysis", 0, AbilityName.PRODUCT_MANAGER)
        val development = step(boardId, "Development", 1, AbilityName.DEVELOPER)
        val pm = worker("Paula", AbilityName.PRODUCT_MANAGER, Seniority.SR)
        val dev = worker("Diego", AbilityName.DEVELOPER, Seniority.PL)

        assertTrue(analysis.canAssign(pm))
        assertFalse(analysis.canAssign(dev))
        val firstAssignments = analysis.assignWorker(pm, emptyMap())
        assertEquals(analysis.id, firstAssignments[pm])
        assertThrows<IllegalArgumentException> { development.assignWorker(pm, firstAssignments) }
        assertThrows<IllegalArgumentException> { analysis.ensureCanAssign(dev) }
    }

    private fun step(
        boardId: BoardId,
        name: String,
        position: Int,
        ability: AbilityName,
    ): Step =
        Step.create(
            boardId = boardId,
            name = name,
            position = position,
            requiredAbility = ability,
        )

    private fun worker(
        name: String,
        ability: AbilityName,
        seniority: Seniority,
    ): Worker =
        Worker(
            name = name,
            abilities = setOf(Ability(name = ability, seniority = seniority)),
        )
}
