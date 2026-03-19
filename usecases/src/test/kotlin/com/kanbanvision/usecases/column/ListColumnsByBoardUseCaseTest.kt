package com.kanbanvision.usecases.column

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListColumnsByBoardUseCaseTest {
    private val columnRepository = mockk<ColumnRepository>()
    private val useCase = ListColumnsByBoardUseCase(columnRepository)

    private val boardId = BoardId.generate()

    @Test
    fun `execute returns list of columns`() =
        runTest {
            val columns =
                listOf(
                    Column(
                        id = ColumnId.generate(),
                        boardId = boardId,
                        name = "To Do",
                        position = 0,
                        requiredAbility = AbilityName.PRODUCT_MANAGER,
                    ),
                    Column(
                        id = ColumnId.generate(),
                        boardId = boardId,
                        name = "In Progress",
                        position = 1,
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )
            coEvery { columnRepository.findByBoardId(boardId) } returns columns.right()

            val result = useCase.execute(ListColumnsByBoardQuery(boardId = boardId.value))

            assertTrue(result.isRight())
            assertEquals(2, result.getOrNull()?.size)
        }

    @Test
    fun `execute returns empty list when no columns exist`() =
        runTest {
            coEvery { columnRepository.findByBoardId(any()) } returns emptyList<Column>().right()

            val result = useCase.execute(ListColumnsByBoardQuery(boardId = boardId.value))

            assertTrue(result.isRight())
            assertEquals(0, result.getOrNull()?.size)
        }

    @Test
    fun `execute with blank board id returns ValidationError`() =
        runTest {
            val result = useCase.execute(ListColumnsByBoardQuery(boardId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when repository returns error`() =
        runTest {
            coEvery { columnRepository.findByBoardId(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(ListColumnsByBoardQuery(boardId = boardId.value))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
