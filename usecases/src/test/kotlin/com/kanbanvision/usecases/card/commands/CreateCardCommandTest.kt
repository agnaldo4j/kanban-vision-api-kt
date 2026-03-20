package com.kanbanvision.usecases.card.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateCardCommandTest {
    @Test
    fun `validate passes with valid data`() {
        assertTrue(CreateCardCommand(stepId = "col-1", title = "Task").validate().isRight())
    }

    @Test
    fun `validate returns error with blank step id`() {
        val result = CreateCardCommand(stepId = "", title = "Task").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with blank title`() {
        val result = CreateCardCommand(stepId = "col-1", title = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with whitespace-only title`() {
        val result = CreateCardCommand(stepId = "col-1", title = "  ").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate accumulates all errors when both fields are blank`() {
        val result = CreateCardCommand(stepId = "", title = "").validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.message.contains("Step id must not be blank"))
        assertTrue(error.message.contains("Card title must not be blank"))
    }
}
