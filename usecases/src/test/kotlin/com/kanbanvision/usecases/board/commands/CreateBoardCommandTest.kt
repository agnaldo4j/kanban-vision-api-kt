package com.kanbanvision.usecases.board.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateBoardCommandTest {
    @Test
    fun `validate passes with valid name`() {
        assertTrue(CreateBoardCommand(name = "My Board").validate().isRight())
    }

    @Test
    fun `validate returns error with blank name`() {
        val result = CreateBoardCommand(name = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with whitespace-only name`() {
        val result = CreateBoardCommand(name = "   ").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
