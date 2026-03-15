package com.kanbanvision.usecases.card.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateCardCommandTest {
    @Test
    fun `validate passes with valid data`() {
        assertTrue(CreateCardCommand(columnId = "col-1", title = "Task").validate().isRight())
    }

    @Test
    fun `validate returns error with blank column id`() {
        val result = CreateCardCommand(columnId = "", title = "Task").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with blank title`() {
        val result = CreateCardCommand(columnId = "col-1", title = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with whitespace-only title`() {
        val result = CreateCardCommand(columnId = "col-1", title = "  ").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
