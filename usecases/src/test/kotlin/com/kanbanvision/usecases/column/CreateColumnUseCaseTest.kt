package com.kanbanvision.usecases.column

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.usecases.column.commands.CreateColumnCommand
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
    private val useCase = CreateColumnUseCase(columnRepository)

    @Test
    fun `execute saves column and returns its id`() =
        runTest {
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Column>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Column>().right() }

            val command = CreateColumnCommand(boardId = "board-1", name = "To Do")
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
            val command = CreateColumnCommand(boardId = "board-1", name = "")

            val result = useCase.execute(command)

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { columnRepository.save(any()) }
        }

    @Test
    fun `execute sets position based on existing columns count`() =
        runTest {
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Column>().right()
            coEvery { columnRepository.save(any()) } answers { firstArg<Column>().right() }

            val result = useCase.execute(CreateColumnCommand(boardId = "board-1", name = "First"))
            assertTrue(result.isRight())
        }

    @Test
    fun `execute returns PersistenceError when repository returns error`() =
        runTest {
            coEvery { columnRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(CreateColumnCommand(boardId = "board-1", name = "To Do"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
