package com.kanbanvision.usecases.column.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateColumnCommandTest {
    @Test
    fun `validate passes with valid data`() {
        assertTrue(CreateColumnCommand(boardId = "board-1", name = "To Do").validate().isRight())
    }

    @Test
    fun `validate returns error with blank board id`() {
        val result = CreateColumnCommand(boardId = "", name = "To Do").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with blank name`() {
        val result = CreateColumnCommand(boardId = "board-1", name = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with whitespace-only name`() {
        val result = CreateColumnCommand(boardId = "board-1", name = "  ").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
