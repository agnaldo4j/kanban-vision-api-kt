package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `PersistenceError holds message`() {
        val error = DomainError.PersistenceError("connection failed")
        assertIs<DomainError>(error)
        assertEquals("connection failed", error.message)
    }
}
