package com.kanbanvision.usecases.scenario.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetFlowMetricsRangeQueryTest {
    @Test
    fun `validate returns Unit when query is valid`() {
        val result = GetFlowMetricsRangeQuery(scenarioId = "scenario-1", fromDay = 1, toDay = 5).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate returns Unit when fromDay equals toDay`() {
        val result = GetFlowMetricsRangeQuery(scenarioId = "scenario-1", fromDay = 3, toDay = 3).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate returns error when scenarioId is blank`() {
        val result = GetFlowMetricsRangeQuery(scenarioId = "", fromDay = 1, toDay = 5).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error when fromDay is zero`() {
        val result = GetFlowMetricsRangeQuery(scenarioId = "scenario-1", fromDay = 0, toDay = 5).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error when toDay is less than fromDay`() {
        val result = GetFlowMetricsRangeQuery(scenarioId = "scenario-1", fromDay = 5, toDay = 3).validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.message.contains("toDay must be greater than or equal to fromDay"))
    }

    @Test
    fun `validate accumulates all errors when all fields are invalid`() {
        val result = GetFlowMetricsRangeQuery(scenarioId = "", fromDay = 0, toDay = -1).validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.message.contains("Scenario id must not be blank"))
        assertTrue(error.message.contains("fromDay must be at least 1"))
    }
}
