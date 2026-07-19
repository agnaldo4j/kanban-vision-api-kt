package com.kanbanvision.domain.errors

import com.kanbanvision.domain.common.errors.CommonError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DomainErrorTest {
    @Test
    fun `given validation messages when exposing message then they are joined in declaration order`() {
        val error = CommonError.ValidationError(listOf("a", "b"))

        assertEquals("a; b", error.message)
    }

    @Test
    fun `given empty validation message list when constructing error then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CommonError.ValidationError(emptyList())
        }
    }

    @Test
    fun `given non positive day when creating day already executed then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            SimulationError.DayAlreadyExecuted(0)
        }
    }
}
