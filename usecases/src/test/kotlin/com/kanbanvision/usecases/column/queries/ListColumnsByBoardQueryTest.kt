package com.kanbanvision.usecases.column.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListColumnsByBoardQueryTest {
    @Test
    fun `validate passes with valid board id`() {
        assertTrue(ListColumnsByBoardQuery(boardId = "board-1").validate().isRight())
    }

    @Test
    fun `validate returns error with blank board id`() {
        val result = ListColumnsByBoardQuery(boardId = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
