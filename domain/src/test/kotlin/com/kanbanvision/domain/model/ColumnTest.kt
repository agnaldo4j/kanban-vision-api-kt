package com.kanbanvision.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColumnTest {
    private val boardId = UUID.randomUUID().toString()

    @Test
    fun `create column with valid data`() {
        val column =
            Step.create(
                boardId = boardId,
                name = "Todo",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        assertEquals("Todo", column.name)
        assertEquals(boardId, column.boardId)
        assertEquals(0, column.position)
        assertEquals(AbilityName.PRODUCT_MANAGER, column.requiredAbility)
        assertTrue(column.id.isNotBlank())
        assertTrue(column.cards.isEmpty())
    }

    @Test
    fun `create column at non-zero position`() {
        val column =
            Step.create(
                boardId = boardId,
                name = "Done",
                position = 2,
                requiredAbility = AbilityName.TESTER,
            )
        assertEquals(2, column.position)
    }

    @Test
    fun `create column with required ability`() {
        val column =
            Step.create(
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            )

        assertEquals(AbilityName.DEVELOPER, column.requiredAbility)
    }

    @Test
    fun `column accepts worker with matching ability`() {
        val column = Step.create(boardId = boardId, name = "Development", position = 1, requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "Ana",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )

        assertTrue(column.canAssign(worker))
        column.ensureCanAssign(worker)
    }

    @Test
    fun `column rejects worker without required ability`() {
        val column = Step.create(boardId = boardId, name = "Development", position = 1, requiredAbility = AbilityName.DEVELOPER)
        val worker =
            Worker(
                name = "Bruno",
                abilities =
                    setOf(
                        Ability(name = AbilityName.TESTER, seniority = Seniority.SR),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR),
                    ),
            )

        assertFalse(column.canAssign(worker))
        assertThrows<IllegalArgumentException> { column.ensureCanAssign(worker) }
    }

    @Test
    fun `create column with blank name throws`() {
        assertThrows<IllegalArgumentException> {
            Step.create(boardId = boardId, name = "", position = 0, requiredAbility = AbilityName.PRODUCT_MANAGER)
        }
    }

    @Test
    fun `create column with whitespace-only name throws`() {
        assertThrows<IllegalArgumentException> {
            Step.create(boardId = boardId, name = "  ", position = 0, requiredAbility = AbilityName.PRODUCT_MANAGER)
        }
    }

    @Test
    fun `create column with negative position throws`() {
        assertThrows<IllegalArgumentException> {
            Step.create(boardId = boardId, name = "Todo", position = -1, requiredAbility = AbilityName.PRODUCT_MANAGER)
        }
    }

    @Test
    fun `column rejects assigning same worker to two different columns`() {
        val productColumn =
            Step.create(
                boardId = boardId,
                name = "Product Discovery",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            )
        val devColumn =
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

        val firstAssignments = productColumn.assignWorker(worker, emptyMap())
        assertEquals(productColumn.id, firstAssignments[worker])
        assertThrows<IllegalArgumentException> { devColumn.assignWorker(worker, firstAssignments) }
    }
}
