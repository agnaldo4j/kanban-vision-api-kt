package com.kanbanvision.usecases.card

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.StepRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val stepRepository = mockk<StepRepository>()
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = CreateCardUseCase(cardRepository, stepRepository, boardRepository)

    private val boardId = UUID.randomUUID().toString()
    private val stepId = UUID.randomUUID().toString()
    private val board = Board(id = boardId, name = "My Board")
    private val step =
        Step(
            id = stepId,
            boardId = boardId,
            name = "To Do",
            position = 0,
            requiredAbility = AbilityName.PRODUCT_MANAGER,
        )

    @Test
    fun `execute saves card and returns its id when step is empty`() =
        runTest {
            coEvery { stepRepository.findById(stepId) } returns step.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByStepId(any()) } returns emptyList<Card>().right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }

            val result = useCase.execute(CreateCardCommand(stepId = stepId, title = "Fix bug"))

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { cardRepository.save(any()) }
        }

    @Test
    fun `execute assigns position equal to existing cards count`() =
        runTest {
            val existingCard = Card.create(stepId = stepId, title = "Existing", position = 0)
            coEvery { stepRepository.findById(stepId) } returns step.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByStepId(any()) } returns listOf(existingCard).right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }

            useCase.execute(CreateCardCommand(stepId = stepId, title = "New Card"))

            coVerify { cardRepository.save(match { it.position == 1 }) }
        }

    @Test
    fun `execute with blank step id returns ValidationError before saving`() =
        runTest {
            val result = useCase.execute(CreateCardCommand(stepId = "", title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute with blank title returns ValidationError before saving`() =
        runTest {
            val result = useCase.execute(CreateCardCommand(stepId = stepId, title = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute returns ValidationError when retrieved board id does not match step boardId`() =
        runTest {
            val otherBoardId = UUID.randomUUID().toString()
            val otherBoard = Board(id = otherBoardId, name = "Other Board")
            coEvery { stepRepository.findById(stepId) } returns step.right()
            coEvery { boardRepository.findById(boardId) } returns otherBoard.right()
            coEvery { cardRepository.findByStepId(any()) } returns emptyList<Card>().right()

            val result = useCase.execute(CreateCardCommand(stepId = stepId, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute returns StepNotFound when step repository returns error`() =
        runTest {
            coEvery { stepRepository.findById(stepId) } returns DomainError.StepNotFound(stepId).left()

            val result = useCase.execute(CreateCardCommand(stepId = stepId, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.StepNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns BoardNotFound when board repository returns error`() =
        runTest {
            coEvery { stepRepository.findById(stepId) } returns step.right()
            coEvery { boardRepository.findById(boardId) } returns DomainError.BoardNotFound(boardId).left()

            val result = useCase.execute(CreateCardCommand(stepId = stepId, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when card repository returns error`() =
        runTest {
            coEvery { stepRepository.findById(stepId) } returns step.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByStepId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(CreateCardCommand(stepId = stepId, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
