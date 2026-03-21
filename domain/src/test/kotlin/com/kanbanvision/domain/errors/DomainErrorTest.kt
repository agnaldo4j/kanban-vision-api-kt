package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DomainErrorTest {
    @Test
    fun `given validation messages when exposing message then they are joined in declaration order`() {
        val error = DomainError.ValidationError(listOf("a", "b"))

        assertEquals("a; b", error.message)
    }

    @Test
    fun `given empty validation message list when constructing error then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DomainError.ValidationError(emptyList())
        }
    }

    @Test
    fun `given non positive day when creating day already executed then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            DomainError.DayAlreadyExecuted(0)
        }
    }
}
