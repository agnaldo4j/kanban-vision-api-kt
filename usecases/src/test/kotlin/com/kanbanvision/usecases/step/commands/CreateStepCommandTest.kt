package com.kanbanvision.usecases.step.commands

import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.team.AbilityName
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateStepCommandTest {
    @Test
    fun `validate passes with valid data`() {
        val result =
            CreateStepCommand(
                boardId = "board-1",
                name = "Analysis",
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            ).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate returns error with blank board id`() {
        val result =
            CreateStepCommand(
                boardId = "",
                name = "Analysis",
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            ).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with blank name`() {
        val result =
            CreateStepCommand(
                boardId = "board-1",
                name = "",
                requiredAbility = AbilityName.PRODUCT_MANAGER,
            ).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
