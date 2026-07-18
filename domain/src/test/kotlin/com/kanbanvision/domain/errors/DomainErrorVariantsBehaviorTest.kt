package com.kanbanvision.domain.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DomainErrorVariantsBehaviorTest {
    @Test
    fun `given each domain error variant when created then payload is preserved`() {
        val board = KanbanError.BoardNotFound(id = "b-1")
        val card = KanbanError.CardNotFound(id = "c-1")
        val step = KanbanError.StepNotFound(id = "s-1")
        val org = KanbanError.OrganizationNotFound(id = "o-1")
        val simulation = SimulationError.SimulationNotFound(id = "sim-1")
        val persistence = CommonError.PersistenceError(message = "db")
        val invalidDecision = SimulationError.InvalidDecision(reason = "invalid")
        val serviceUnavailable = CommonError.ServiceUnavailable(service = "database", reason = "circuit breaker open")
        val forbidden = CommonError.Forbidden(reason = "cross-tenant access denied")

        assertIs<KanbanError.BoardNotFound>(board)
        assertEquals("b-1", board.id)
        assertEquals("c-1", card.id)
        assertEquals("s-1", step.id)
        assertEquals("o-1", org.id)
        assertEquals("sim-1", simulation.id)
        assertEquals("db", persistence.message)
        assertEquals("invalid", invalidDecision.reason)
        assertEquals("database", serviceUnavailable.service)
        assertEquals("circuit breaker open", serviceUnavailable.reason)
        assertIs<CommonError.Forbidden>(forbidden)
        assertEquals("cross-tenant access denied", forbidden.reason)
    }

    @Test
    fun `given validation error variants when building then message list contracts are preserved`() {
        val fromMessages = CommonError.ValidationError(messages = listOf("a", "b"))
        val fromSingleMessage = CommonError.ValidationError(message = "single")

        assertEquals(listOf("a", "b"), fromMessages.messages)
        assertEquals("a; b", fromMessages.message)
        assertEquals(listOf("single"), fromSingleMessage.messages)
        assertEquals("single", fromSingleMessage.message)

        assertFailsWith<IllegalArgumentException> {
            CommonError.ValidationError(messages = emptyList())
        }
    }
}
