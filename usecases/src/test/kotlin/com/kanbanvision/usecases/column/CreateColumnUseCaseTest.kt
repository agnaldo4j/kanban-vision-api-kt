package com.kanbanvision.usecases.column

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.ColumnRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateColumnUseCaseTest {
    private val columnRepository = mockk<ColumnRepository>()
    private val boardRepository = mockk<BoardRepository>()
    private val useCase = CreateColumnUseCase(columnRepository, boardRepository)

    private val boardId = BoardId.generate()
    private val board = Board(id = boardId, name = "My Board")

    @Test
    fun `execute saves column and returns its id`() =
        runTest {
            coEvery { boardRepository.findById(any()) } returns board.right()
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Column>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Column>().right() }

            val command = CreateColumnCommand(boardId = boardId.value, name = "To Do")
            val result = useCase.execute(command)

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
            coVerify(exactly = 1) { columnRepository.save(any()) }
        }

    @Test
    fun `execute with blank board id returns ValidationError before saving`() =
        runTest {
            val command = CreateColumnCommand(boardId = "", name = "To Do")

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { columnRepository.save(any()) }
        }

    @Test
    fun `execute with blank name returns ValidationError before saving`() =
        runTest {
            val command = CreateColumnCommand(boardId = boardId.value, name = "")

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { columnRepository.save(any()) }
        }

    @Test
    fun `execute sets position based on existing columns count`() =
        runTest {
            coEvery { boardRepository.findById(any()) } returns board.right()
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Column>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Column>().right() }

            val result = useCase.execute(CreateColumnCommand(boardId = boardId.value, name = "First"))
            assertTrue(result.isRight())
        }

    @Test
    fun `execute returns ValidationError when column name already exists on board`() =
        runTest {
            val existingColumn = Column.create(boardId = boardId, name = "To Do", position = 0)
            coEvery { boardRepository.findById(any()) } returns board.right()
            coEvery { columnRepository.findByBoardId(any()) } returns listOf(existingColumn).right()

            val result = useCase.execute(CreateColumnCommand(boardId = boardId.value, name = "To Do"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { columnRepository.save(any()) }
        }

    @Test
    fun `execute returns PersistenceError when board repository returns error`() =
        runTest {
            coEvery { boardRepository.findById(any()) } returns DomainError.BoardNotFound(boardId.value).left()

            val result = useCase.execute(CreateColumnCommand(boardId = boardId.value, name = "To Do"))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when column repository returns error`() =
        runTest {
            coEvery { boardRepository.findById(any()) } returns board.right()
            coEvery { columnRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(CreateColumnCommand(boardId = boardId.value, name = "To Do"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
