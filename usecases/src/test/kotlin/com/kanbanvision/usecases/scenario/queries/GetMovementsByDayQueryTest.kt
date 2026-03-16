package com.kanbanvision.usecases.scenario.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetMovementsByDayQueryTest {
    @Test
    fun `validate returns Unit when query is valid`() {
        val result = GetMovementsByDayQuery(scenarioId = "scenario-1", day = 1).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate returns error when scenarioId is blank`() {
        val result = GetMovementsByDayQuery(scenarioId = "", day = 1).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error when day is zero`() {
        val result = GetMovementsByDayQuery(scenarioId = "scenario-1", day = 0).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate returns error when day is negative`() {
        val result = GetMovementsByDayQuery(scenarioId = "scenario-1", day = -1).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate accumulates all errors when both fields are invalid`() {
        val result = GetMovementsByDayQuery(scenarioId = "", day = 0).validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.message.contains("Scenario id must not be blank"))
        assertTrue(error.message.contains("Day must be at least 1"))
    }
}
