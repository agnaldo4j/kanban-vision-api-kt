package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DomainErrorVariantsBehaviorTest {
    @Test
    fun `given each domain error variant when created then payload is preserved`() {
        val board = DomainError.BoardNotFound(id = "b-1")
        val card = DomainError.CardNotFound(id = "c-1")
        val step = DomainError.StepNotFound(id = "s-1")
        val org = DomainError.OrganizationNotFound(id = "o-1")
        val simulation = DomainError.SimulationNotFound(id = "sim-1")
        val persistence = DomainError.PersistenceError(message = "db")
        val invalidDecision = DomainError.InvalidDecision(reason = "invalid")

        assertIs<DomainError.BoardNotFound>(board)
        assertEquals("b-1", board.id)
        assertEquals("c-1", card.id)
        assertEquals("s-1", step.id)
        assertEquals("o-1", org.id)
        assertEquals("sim-1", simulation.id)
        assertEquals("db", persistence.message)
        assertEquals("invalid", invalidDecision.reason)
    }

    @Test
    fun `given validation error variants when building then message list contracts are preserved`() {
        val fromMessages = DomainError.ValidationError(messages = listOf("a", "b"))
        val fromSingleMessage = DomainError.ValidationError(message = "single")

        assertEquals(listOf("a", "b"), fromMessages.messages)
        assertEquals("a; b", fromMessages.message)
        assertEquals(listOf("single"), fromSingleMessage.messages)
        assertEquals("single", fromSingleMessage.message)

        assertFailsWith<IllegalArgumentException> {
            DomainError.ValidationError(messages = emptyList())
        }
    }
}
