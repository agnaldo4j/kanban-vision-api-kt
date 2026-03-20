package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StepAssignmentTest {
    private val boardId = UUID.randomUUID().toString()

    @Test
    fun `create step with valid data`() {
        val step =
            Step.create(
                boardId = boardId,
                name = "Todo",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        assertEquals("Todo", step.name)
        assertEquals(boardId, step.boardId)
        assertEquals(0, step.position)
        assertEquals(AbilityName.PRODUCT_MANAGER, step.requiredAbility)
        assertTrue(step.id.isNotBlank())
        assertTrue(step.cards.isEmpty())
    }

    @Test
    fun `create step at non-zero position`() {
        val step =
            Step.create(
                boardId = boardId,
                name = "Done",
                position = 2,
                requiredAbility = AbilityName.TESTER,
            )
        assertEquals(2, step.position)
    }

    @Test
    fun `create step with required ability`() {
        val step =
            Step.create(
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            )

        assertEquals(AbilityName.DEVELOPER, step.requiredAbility)
    }

    @Test
    fun `step accepts worker with matching ability`() {
        val step = Step.create(boardId = boardId, name = "Development", position = 1, requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "Ana",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )

        assertTrue(step.canAssign(worker))
        step.ensureCanAssign(worker)
    }

    @Test
    fun `step rejects worker without required ability`() {
        val step = Step.create(boardId = boardId, name = "Development", position = 1, requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "Bruno",
                abilities =
                    setOf(
                        Ability(name = AbilityName.TESTER, seniority = Seniority.SR),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR),
                    ),
            )

        assertFalse(step.canAssign(worker))
        assertThrows<IllegalArgumentException> { step.ensureCanAssign(worker) }
    }

    @Test
    fun `create step with blank name throws`() {
        assertThrows<IllegalArgumentException> {
            Step.create(boardId = boardId, name = "", position = 0, requiredAbility = AbilityName.PRODUCT_MANAGER)
        }
    }

    @Test
    fun `create step with whitespace-only name throws`() {
        assertThrows<IllegalArgumentException> {
            Step.create(boardId = boardId, name = "  ", position = 0, requiredAbility = AbilityName.PRODUCT_MANAGER)
        }
    }

    @Test
    fun `create step with negative position throws`() {
        assertThrows<IllegalArgumentException> {
            Step.create(boardId = boardId, name = "Todo", position = -1, requiredAbility = AbilityName.PRODUCT_MANAGER)
        }
    }

    @Test
    fun `step rejects assigning same worker to two different steps`() {
        val productStep =
            Step.create(
                boardId = boardId,
                name = "Product Discovery",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val devStep =
            Step.create(
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val worker =
            Worker(
                name = "Paula",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.SR)),
            )

        val firstAssignments = productStep.assignWorker(worker, emptyMap())
        assertEquals(productStep.id, firstAssignments[worker])
        assertThrows<IllegalArgumentException> { devStep.assignWorker(worker, firstAssignments) }
    }
}
