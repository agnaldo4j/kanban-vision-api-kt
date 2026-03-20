package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DomainErrorTest {
    @Test
    fun `ValidationError holds message`() {
        val error = DomainError.ValidationError("invalid input")
        assertIs<DomainError>(error)
        assertEquals("invalid input", error.message)
    }

    @Test
    fun `BoardNotFound holds id`() {
        val error = DomainError.BoardNotFound("board-1")
        assertIs<DomainError>(error)
        assertEquals("board-1", error.id)
    }

    @Test
    fun `CardNotFound holds id`() {
        val error = DomainError.CardNotFound("card-1")
        assertIs<DomainError>(error)
        assertEquals("card-1", error.id)
    }

    @Test
    fun `ColumnNotFound holds id`() {
        val error = DomainError.ColumnNotFound("col-1")
        assertIs<DomainError>(error)
        assertEquals("col-1", error.id)
    }

    @Test
    fun `StepNotFound holds id`() {
        val error = DomainError.StepNotFound("step-1")
        assertIs<DomainError>(error)
        assertEquals("step-1", error.id)
    }

    @Test
    fun `PersistenceError holds message`() {
        val error = DomainError.PersistenceError("connection failed")
        assertIs<DomainError>(error)
        assertEquals("connection failed", error.message)
    }

    @Test
    fun `TenantNotFound holds id`() {
        val error = DomainError.TenantNotFound("tenant-1")
        assertIs<DomainError>(error)
        assertEquals("tenant-1", error.id)
    }

    @Test
    fun `ScenarioNotFound holds id`() {
        val error = DomainError.ScenarioNotFound("scenario-1")
        assertIs<DomainError>(error)
        assertEquals("scenario-1", error.id)
    }

    @Test
    fun `InvalidDecision holds reason`() {
        val error = DomainError.InvalidDecision("item not in correct state")
        assertIs<DomainError>(error)
        assertEquals("item not in correct state", error.reason)
    }

    @Test
    fun `DayAlreadyExecuted holds day number`() {
        val error = DomainError.DayAlreadyExecuted(5)
        assertIs<DomainError>(error)
        assertEquals(5, error.day)
    }

    @Test
    fun `DayAlreadyExecuted rejects day zero`() {
        assertFailsWith<IllegalArgumentException> { DomainError.DayAlreadyExecuted(0) }
    }

    @Test
    fun `DayAlreadyExecuted rejects negative day`() {
        assertFailsWith<IllegalArgumentException> { DomainError.DayAlreadyExecuted(-1) }
    }
}
