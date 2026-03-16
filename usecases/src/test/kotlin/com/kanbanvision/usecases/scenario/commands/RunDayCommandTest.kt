package com.kanbanvision.usecases.scenario.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunDayCommandTest {
    @Test
    fun `validate passes with valid scenarioId`() {
        val result = RunDayCommand(scenarioId = "s-1", decisions = emptyList()).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate fails when scenarioId is blank`() {
        val result = RunDayCommand(scenarioId = "", decisions = emptyList()).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
