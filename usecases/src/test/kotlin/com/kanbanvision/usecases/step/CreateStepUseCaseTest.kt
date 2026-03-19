package com.kanbanvision.usecases.step

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.column.CreateColumnUseCase
import com.kanbanvision.usecases.step.commands.CreateStepCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateStepUseCaseTest {
    private val createColumnUseCase = mockk<CreateColumnUseCase>()
    private val useCase = CreateStepUseCase(createColumnUseCase)

    @Test
    fun `execute delegates to CreateColumnUseCase and returns id`() =
        runTest {
            val expectedId = ColumnId.generate()
            coEvery { createColumnUseCase.execute(any()) } returns expectedId.right()

            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = "board-1",
                        name = "Development",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isRight())
            assertEquals(expectedId, result.getOrNull())
        }

    @Test
    fun `execute propagates domain error from CreateColumnUseCase`() =
        runTest {
            coEvery { createColumnUseCase.execute(any()) } returns DomainError.PersistenceError("db").left()

            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = "board-1",
                        name = "Development",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `execute validates step command before delegating`() =
        runTest {
            val result =
                useCase.execute(
                    CreateStepCommand(
                        boardId = "board-1",
                        name = "",
                        requiredAbility = AbilityName.DEVELOPER,
                    ),
                )

            assertTrue(result.isLeft())
            val error = assertIs<DomainError.ValidationError>(result.leftOrNull())
            assertEquals("Step name must not be blank", error.message)
            coVerify(exactly = 0) { createColumnUseCase.execute(any()) }
        }
}
