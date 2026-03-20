package com.kanbanvision.persistence.repositories

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.repositories.ColumnRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JdbcStepRepositoryTest {
    private val delegate = mockk<ColumnRepository>()
    private val repository = JdbcStepDomainRepository(delegate)

    @Test
    fun `save maps step to column and back`() =
        runBlocking {
            val step = sampleStep()
            coEvery { delegate.save(any()) } answers { firstArg<Step>().right() }

            val result = repository.save(step)

            assertTrue(result.isRight())
            assertEquals(step, result.getOrNull())
        }

    @Test
    fun `findById maps column not found to step not found`() =
        runBlocking {
            val stepId = UUID.randomUUID().toString()
            coEvery { delegate.findById(stepId) } returns DomainError.ColumnNotFound(stepId).left()

            val result = repository.findById(stepId)

            assertTrue(result.isLeft())
            val error = assertIs<DomainError.StepNotFound>(result.leftOrNull())
            assertEquals(stepId, error.id)
        }

    @Test
    fun `findByBoardId maps columns to steps`() =
        runBlocking {
            val boardId = UUID.randomUUID().toString()
            val step = sampleStep(boardId = boardId)
            val column =
                Step(
                    id = step.id,
                    boardId = boardId,
                    name = step.name,
                    position = step.position,
                    requiredAbility = step.requiredAbility,
                    cards = step.cards,
                )
            coEvery { delegate.findByBoardId(boardId) } returns listOf(column).right()

            val result = repository.findByBoardId(boardId)

            assertTrue(result.isRight())
            assertEquals(listOf(step), result.getOrNull())
        }

    private fun sampleStep(boardId: String = UUID.randomUUID().toString()): Step =
        Step(
            id = UUID.randomUUID().toString(),
            boardId = boardId,
            name = "Development",
            position = 1,
            requiredAbility = AbilityName.DEVELOPER,
        )
}
