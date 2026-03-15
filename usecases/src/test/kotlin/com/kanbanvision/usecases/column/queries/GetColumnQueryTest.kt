package com.kanbanvision.usecases.column.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetColumnQueryTest {
    @Test
    fun `validate passes with valid id`() {
        assertTrue(GetColumnQuery(id = "col-1").validate().isRight())
    }

    @Test
    fun `validate returns error with blank id`() {
        val result = GetColumnQuery(id = "").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
