package com.kanbanvision.usecases.step.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListStepsByBoardQueryTest {
    @Test
    fun `validate passes with valid board id`() {
        assertTrue(ListStepsByBoardQuery("board-1").validate().isRight())
    }

    @Test
    fun `validate fails with blank board id`() {
        val result = ListStepsByBoardQuery("").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
