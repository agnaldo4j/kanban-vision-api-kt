package com.kanbanvision.usecases.scenario.commands

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateScenarioCommandTest {
    @Test
    fun `validate passes with valid inputs`() {
        val result = CreateScenarioCommand(tenantId = "t-1", wipLimit = 3, teamSize = 2, seedValue = 42L).validate()
        assertTrue(result.isRight())
    }

    @Test
    fun `validate fails when tenantId is blank`() {
        val result = CreateScenarioCommand(tenantId = "", wipLimit = 3, teamSize = 2, seedValue = 42L).validate()
        assertTrue(result.isLeft())
        assertIs<DomainError.ValidationError>(result.leftOrNull())
    }

    @Test
    fun `validate fails when wipLimit is zero`() {
        val result = CreateScenarioCommand(tenantId = "t-1", wipLimit = 0, teamSize = 2, seedValue = 42L).validate()
        assertTrue(result.isLeft())
    }

    @Test
    fun `validate fails when teamSize is zero`() {
        val result = CreateScenarioCommand(tenantId = "t-1", wipLimit = 3, teamSize = 0, seedValue = 42L).validate()
        assertTrue(result.isLeft())
    }

    @Test
    fun `validate accumulates all errors when multiple fields are invalid`() {
        val result = CreateScenarioCommand(tenantId = "", wipLimit = 0, teamSize = 0, seedValue = 0L).validate()
        assertTrue(result.isLeft())
        val error = result.leftOrNull()
        assertIs<DomainError.ValidationError>(error)
        assertTrue(error.messages.size >= 2)
    }
}
