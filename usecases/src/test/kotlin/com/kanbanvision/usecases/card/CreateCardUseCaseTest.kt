package com.kanbanvision.usecases.card

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.ColumnRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateCardUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val columnRepository = mockk<ColumnRepository>()
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = CreateCardUseCase(cardRepository, columnRepository, boardRepository)

    private val boardId = BoardId.generate()
    private val columnId = ColumnId.generate()
    private val board = Board(id = boardId, name = "My Board")
    private val column = Column(id = columnId, boardId = boardId, name = "To Do", position = 0)

    @Test
    fun `execute saves card and returns its id when column is empty`() =
        runTest {
            coEvery { columnRepository.findById(columnId) } returns column.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByColumnId(any()) } returns emptyList<Card>().right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }

            val result = useCase.execute(CreateCardCommand(columnId = columnId.value, title = "Fix bug"))

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { cardRepository.save(any()) }
        }

    @Test
    fun `execute assigns position equal to existing cards count`() =
        runTest {
            val existingCard = Card.create(columnId = columnId, title = "Existing", position = 0)
            coEvery { columnRepository.findById(columnId) } returns column.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByColumnId(any()) } returns listOf(existingCard).right()
            coEvery { cardRepository.save(any()) } answers { firstArg<Card>().right() }

            useCase.execute(CreateCardCommand(columnId = columnId.value, title = "New Card"))

            coVerify { cardRepository.save(match { it.position == 1 }) }
        }

    @Test
    fun `execute with blank column id returns ValidationError before saving`() =
        runTest {
            val result = useCase.execute(CreateCardCommand(columnId = "", title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute with blank title returns ValidationError before saving`() =
        runTest {
            val result = useCase.execute(CreateCardCommand(columnId = columnId.value, title = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute returns ValidationError when column does not belong to board`() =
        runTest {
            val otherBoardId = BoardId.generate()
            val otherBoard = Board(id = otherBoardId, name = "Other Board")
            coEvery { columnRepository.findById(columnId) } returns column.right()
            coEvery { boardRepository.findById(boardId) } returns otherBoard.right()
            coEvery { cardRepository.findByColumnId(any()) } returns emptyList<Card>().right()

            val result = useCase.execute(CreateCardCommand(columnId = columnId.value, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { cardRepository.save(any()) }
        }

    @Test
    fun `execute returns ColumnNotFound when column repository returns error`() =
        runTest {
            coEvery { columnRepository.findById(columnId) } returns DomainError.ColumnNotFound(columnId.value).left()

            val result = useCase.execute(CreateCardCommand(columnId = columnId.value, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ColumnNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when card repository returns error`() =
        runTest {
            coEvery { columnRepository.findById(columnId) } returns column.right()
            coEvery { boardRepository.findById(boardId) } returns board.right()
            coEvery { cardRepository.findByColumnId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(CreateCardCommand(columnId = columnId.value, title = "Task"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
