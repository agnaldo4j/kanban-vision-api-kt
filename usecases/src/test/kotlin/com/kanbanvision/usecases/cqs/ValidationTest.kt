package com.kanbanvision.usecases.cqs

import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.common.errors.CommonError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun `given a validator raising several messages when accumulating then each one stays a separate entry`() {
        val result =
            accumulateValidation {
                zipOrAccumulate(
                    { raise(CommonError.ValidationError(listOf("first", "second"))) },
                    { raise(CommonError.ValidationError("third")) },
                ) { _, _ -> }
            }

        val error = result.leftOrNull()
        assertIs<CommonError.ValidationError>(error)
        assertEquals(listOf("first", "second", "third"), error.messages)
    }

    @Test
    fun `given every check passing when accumulating then the result is right`() {
        val result = accumulateValidation { }

        assertTrue(result.isRight())
    }

    @Test
    fun `given a valid simulation reference when validating then the result is right`() {
        val result = validateSimulationRef(simulationId = "sim-1", callerOrganizationId = "org-1")

        assertTrue(result.isRight())
    }
}
