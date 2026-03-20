package com.kanbanvision.usecases.step

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.step.queries.GetStepQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetStepUseCaseTest {
    private val getColumnUseCase = mockk<GetColumnUseCase>()
    private val useCase = GetStepUseCase(getColumnUseCase)

    @Test
    fun `execute delegates to GetColumnUseCase and returns step`() =
        runTest {
            val expectedStep: Step =
                Step(
                    id = ColumnId.generate(),
                    boardId = BoardId.generate(),
                    name = "Analysis",
                    position = 0,
                    requiredAbility = AbilityName.PRODUCT_MANAGER,
                )
            coEvery { getColumnUseCase.execute(any()) } returns expectedStep.right()

            val result = useCase.execute(GetStepQuery(id = expectedStep.id.value))

            assertTrue(result.isRight())
            assertEquals(expectedStep, result.getOrNull())
        }

    @Test
    fun `execute propagates error from GetColumnUseCase`() =
        runTest {
            coEvery { getColumnUseCase.execute(any()) } returns DomainError.ColumnNotFound("missing").left()

            val result = useCase.execute(GetStepQuery(id = "missing"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ColumnNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns validation error when step query is invalid`() =
        runTest {
            val result = useCase.execute(GetStepQuery(id = " "))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { getColumnUseCase.execute(any()) }
        }
}
