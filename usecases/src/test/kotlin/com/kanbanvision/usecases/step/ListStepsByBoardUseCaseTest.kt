package com.kanbanvision.usecases.step

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.step.queries.ListStepsByBoardQuery
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListStepsByBoardUseCaseTest {
    private val listColumnsByBoardUseCase = mockk<ListColumnsByBoardUseCase>()
    private val useCase = ListStepsByBoardUseCase(listColumnsByBoardUseCase)

    @Test
    fun `execute delegates to ListColumnsByBoardUseCase and returns steps`() =
        runTest {
            val boardId = BoardId.generate()
            val steps: List<Step> =
                listOf(
                    Step(
                        id = ColumnId.generate(),
                        boardId = boardId,
                        name = "Analysis",
                        position = 0,
                        requiredAbility = AbilityName.PRODUCT_MANAGER,
                    ),
                    Step(
                        id = ColumnId.generate(),
                        boardId = boardId,
                        name = "Development",
                        position = 1,
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )
            coEvery { listColumnsByBoardUseCase.execute(any()) } returns steps.right()

            val result = useCase.execute(ListStepsByBoardQuery(boardId = boardId.value))

            assertTrue(result.isRight())
            assertEquals(steps, result.getOrNull())
        }

    @Test
    fun `execute propagates error from ListColumnsByBoardUseCase`() =
        runTest {
            coEvery { listColumnsByBoardUseCase.execute(any()) } returns DomainError.BoardNotFound("missing").left()

            val result = useCase.execute(ListStepsByBoardQuery(boardId = "missing"))

            assertTrue(result.isLeft())
            assertIs<DomainError.BoardNotFound>(result.leftOrNull())
        }
}
