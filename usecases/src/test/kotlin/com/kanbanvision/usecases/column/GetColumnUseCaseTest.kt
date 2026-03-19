package com.kanbanvision.usecases.column

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.repositories.ColumnRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetColumnUseCaseTest {
    private val columnRepository = mockk<ColumnRepository>()
    private val useCase = GetColumnUseCase(columnRepository)

    private val columnId = ColumnId.generate()
    private val column =
        Column(
            id = columnId,
            boardId = BoardId.generate(),
            name = "To Do",
            position = 0,
            requiredAbility = AbilityName.PRODUCT_MANAGER,
        )

    @Test
    fun `execute returns column when found`() =
        runTest {
            coEvery { columnRepository.findById(columnId) } returns column.right()

            val result = useCase.execute(GetColumnQuery(id = columnId.value))

            assertTrue(result.isRight())
        }

    @Test
    fun `execute returns ColumnNotFound when not found`() =
        runTest {
            coEvery { columnRepository.findById(any()) } returns DomainError.ColumnNotFound("nonexistent").left()

            val result = useCase.execute(GetColumnQuery(id = "nonexistent"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ColumnNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute with blank id returns ValidationError`() =
        runTest {
            val result = useCase.execute(GetColumnQuery(id = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when repository returns error`() =
        runTest {
            coEvery { columnRepository.findById(any()) } returns DomainError.PersistenceError("DB failure").left()

            val result = useCase.execute(GetColumnQuery(id = ColumnId.generate().value))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
