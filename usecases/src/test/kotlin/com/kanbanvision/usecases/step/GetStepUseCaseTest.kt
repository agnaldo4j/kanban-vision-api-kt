package com.kanbanvision.usecases.step

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.step.queries.GetStepQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetStepUseCaseTest {
    private val stepRepository = mockk<StepRepository>()
    private val useCase = GetStepUseCase(stepRepository)

    @Test
    fun `execute returns step by id`() =
        runTest {
            val columnId = UUID.randomUUID().toString()
            val expectedStep =
                Step(
                    id = columnId,
                    boardId = UUID.randomUUID().toString(),
                    name = "Analysis",
                    position = 0,
                    requiredAbility = AbilityName.PRODUCT_MANAGER,
                )
            coEvery { stepRepository.findById(columnId) } returns expectedStep.right()

            val result = useCase.execute(GetStepQuery(id = expectedStep.id))

            assertTrue(result.isRight())
            assertEquals(expectedStep, result.getOrNull())
        }

    @Test
    fun `execute propagates error from repository`() =
        runTest {
            coEvery { stepRepository.findById(any()) } returns DomainError.StepNotFound("missing").left()

            val result = useCase.execute(GetStepQuery(id = "missing"))

            assertTrue(result.isLeft())
            assertIs<DomainError.StepNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns validation error when step query is invalid`() =
        runTest {
            val result = useCase.execute(GetStepQuery(id = " "))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { stepRepository.findById(any()) }
        }
}
