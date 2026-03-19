package com.kanbanvision.usecases.step.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetStepQueryTest {
    @Test
    fun `validate passes with valid id`() {
        assertTrue(GetStepQuery("step-1").validate().isRight())
    }

    @Test
    fun `validate fails with blank id`() {
        val result = GetStepQuery("").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
