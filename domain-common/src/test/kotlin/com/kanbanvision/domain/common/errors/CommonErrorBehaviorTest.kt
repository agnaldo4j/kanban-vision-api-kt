package com.kanbanvision.domain.common.errors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CommonErrorBehaviorTest {
    @Test
    fun `given validation messages when exposing message then they are joined in declaration order`() {
        val error = CommonError.ValidationError(listOf("a", "b"))

        assertEquals("a; b", error.message)
    }

    @Test
    fun `given empty validation message list when constructing error then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CommonError.ValidationError(emptyList())
        }
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

    @Test
    fun `given generic common error variants when created then payload is preserved`() {
        val persistence = CommonError.PersistenceError(message = "db")
        val serviceUnavailable = CommonError.ServiceUnavailable(service = "database", reason = "circuit breaker open")
        val forbidden = CommonError.Forbidden(reason = "cross-tenant access denied")

        assertEquals("db", persistence.message)
        assertEquals("database", serviceUnavailable.service)
        assertEquals("circuit breaker open", serviceUnavailable.reason)
        assertIs<CommonError.Forbidden>(forbidden)
        assertEquals("cross-tenant access denied", forbidden.reason)
    }
}
