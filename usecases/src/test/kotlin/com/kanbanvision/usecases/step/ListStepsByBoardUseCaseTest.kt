package com.kanbanvision.usecases.step

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.queries.ListStepsByBoardQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListStepsByBoardUseCaseTest {
    private val getBoardUseCase = mockk<GetBoardUseCase>()
    private val stepRepository = mockk<StepRepository>()
    private val useCase = ListStepsByBoardUseCase(getBoardUseCase, stepRepository)
    private val board = Board(id = UUID.randomUUID().toString(), name = "Board")

    @Test
    fun `execute lists steps by board`() =
        runTest {
            val boardId = UUID.randomUUID().toString()
            val steps = sampleSteps(boardId)
            coEvery { getBoardUseCase.execute(any()) } returns board.right()
            coEvery { stepRepository.findByBoardId(boardId) } returns steps.right()

            val result = useCase.execute(ListStepsByBoardQuery(boardId = boardId))

            assertTrue(result.isRight())
            assertEquals(steps, result.getOrNull())
        }

    @Test
    fun `execute propagates error from repository`() =
        runTest {
            coEvery { getBoardUseCase.execute(any()) } returns board.right()
            coEvery { stepRepository.findByBoardId(any()) } returns DomainError.BoardNotFound("missing").left()

            val result = useCase.execute(ListStepsByBoardQuery(boardId = "missing"))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns BoardNotFound when board does not exist`() =
        runTest {
            coEvery { getBoardUseCase.execute(any()) } returns DomainError.BoardNotFound("missing").left()

            val result = useCase.execute(ListStepsByBoardQuery(boardId = "missing"))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns validation error when list query is invalid`() =
        runTest {
            val result = useCase.execute(ListStepsByBoardQuery(boardId = " "))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { getBoardUseCase.execute(any()) }
            coVerify(exactly = 0) { stepRepository.findByBoardId(any()) }
        }

    private fun sampleSteps(boardId: String): List<Step> =
        listOf(
            Step(
                id = UUID.randomUUID().toString(),
                boardId = boardId,
                name = "Analysis",
                position = 0,
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            ),
            Step(
                id = UUID.randomUUID().toString(),
                boardId = boardId,
                name = "Development",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            ),
        )
}
