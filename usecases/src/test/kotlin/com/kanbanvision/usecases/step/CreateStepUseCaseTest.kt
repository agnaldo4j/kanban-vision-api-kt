package com.kanbanvision.usecases.step

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.commands.CreateStepCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateStepUseCaseTest {
    private val boardRepository = mockk<BoardRepository>()
    private val stepRepository = mockk<StepRepository>()
    private val useCase = CreateStepUseCase(stepRepository, boardRepository)
    private val boardId = UUID.randomUUID().toString()
    private val board = Board(id = boardId, name = "Delivery")

    @Test
    fun `execute creates step and returns id`() =
        runTest {
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { stepRepository.findByBoardId(boardId) } returns emptyList<Step>().right()
            coEvery { stepRepository.save(any()) } answers { firstArg<Step>().right() }

            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = boardId,
                        name = "Development",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isRight())
            val createdId = result.getOrNull()
            assertTrue(createdId != null)
            assertTrue(createdId.isNotBlank())
            coVerify(exactly = 1) { stepRepository.save(any()) }
        }

    @Test
    fun `execute propagates domain error from repository`() =
        runTest {
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { stepRepository.findByBoardId(boardId) } returns DomainError.PersistenceError("db").left()

            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = boardId,
                        name = "Development",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `execute returns BoardNotFound when board does not exist`() =
        runTest {
            coEvery { boardRepository.findById(boardId) } returns DomainError.BoardNotFound(boardId).left()

            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = boardId,
                        name = "Development",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
            coVerify(exactly = 0) { stepRepository.findByBoardId(any()) }
            coVerify(exactly = 0) { stepRepository.save(any()) }
        }

    @Test
    fun `execute validates step command before delegating`() =
        runTest {
            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = boardId,
                        name = "",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isLeft())
            val error = assertIs<DomainError.ValidationError>(result.leftOrNull())
            assertEquals("Step name must not be blank", error.message)
            coVerify(exactly = 0) { boardRepository.findById(any()) }
            coVerify(exactly = 0) { stepRepository.findByBoardId(any()) }
            coVerify(exactly = 0) { stepRepository.save(any()) }
        }

    @Test
    fun `execute returns validation error when step name already exists on board`() =
        runTest {
            val existingStep =
                Step(
                    id = UUID.randomUUID().toString(),
                    boardId = boardId,
                    name = "Development",
                    position = 0,
                    requiredAbility = AbilityName.DEVELOPER,
                )
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { stepRepository.findByBoardId(boardId) } returns listOf(existingStep).right()

            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = boardId,
                        name = "Development",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isLeft())
            val error = assertIs<DomainError.ValidationError>(result.leftOrNull())
            assertEquals("Step name 'Development' already exists on this board", error.message)
            coVerify(exactly = 0) { stepRepository.save(any()) }
        }
}
