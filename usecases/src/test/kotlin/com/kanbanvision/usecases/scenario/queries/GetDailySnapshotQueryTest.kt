package com.kanbanvision.usecases.scenario.queries

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetDailySnapshotQueryTest {
    @Test
    fun `validate passes with valid inputs`() {
        val result = GetDailySnapshotQuery(scenarioId = "s-1", day = 1).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate fails when scenarioId is blank`() {
        val result = GetDailySnapshotQuery(scenarioId = "", day = 1).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate fails when day is zero`() {
        val result = GetDailySnapshotQuery(scenarioId = "s-1", day = 0).validate()
        assertTrue(result.isLeft())
    }

    @Test
    fun `validate accumulates errors when both fields invalid`() {
        val result = GetDailySnapshotQuery(scenarioId = "", day = 0).validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.messages.size >= 2)
    }
}
