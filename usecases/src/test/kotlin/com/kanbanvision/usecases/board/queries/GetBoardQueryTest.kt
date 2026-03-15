package com.kanbanvision.usecases.board.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetBoardQueryTest {
    @Test
    fun `validate passes with valid id`() {
        assertTrue(GetBoardQuery(id = "some-id").validate().isRight())
    }

    @Test
    fun `validate returns error with blank id`() {
        val result = GetBoardQuery(id = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error with whitespace-only id`() {
        val result = GetBoardQuery(id = "   ").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
