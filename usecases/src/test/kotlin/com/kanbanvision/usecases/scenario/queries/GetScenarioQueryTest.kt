package com.kanbanvision.usecases.scenario.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetScenarioQueryTest {
    @Test
    fun `validate passes with valid scenarioId`() {
        val result = GetScenarioQuery(scenarioId = "s-1").validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate fails when scenarioId is blank`() {
        val result = GetScenarioQuery(scenarioId = "  ").validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }
}
