package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.routes.ColumnResponse
import com.kanbanvision.httpapi.routes.CreateColumnRequest
import com.kanbanvision.httpapi.routes.HealthResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DtoStructuralTest {
    @Test
    fun `CreateColumnRequest equality and copy`() {
        val req = CreateColumnRequest(boardId = "b1", name = "To Do")
        assertEquals(req, req.copy())
        assertEquals("b1", req.boardId)
        assertEquals("To Do", req.name)
        assertNotEquals(req, req.copy(boardId = "b2"))
    }

    @Test
    fun `ColumnResponse equality and copy`() {
        val resp = ColumnResponse(id = "col-1", boardId = "b-1", name = "In Progress", position = 1)
        assertEquals(resp, resp.copy())
        assertEquals("col-1", resp.id)
        assertEquals("b-1", resp.boardId)
        assertEquals("In Progress", resp.name)
        assertEquals(1, resp.position)
        assertNotEquals(resp, resp.copy(id = "col-2"))
    }

    @Test
    fun `ValidationErrorResponse equality and copy`() {
        val resp = ValidationErrorResponse(errors = listOf("Field required"), requestId = "req-1")
        assertEquals(resp, resp.copy())
        assertEquals(1, resp.errors.size)
        assertEquals("req-1", resp.requestId)
        assertNotEquals(resp, resp.copy(requestId = "req-2"))
    }

    @Test
    fun `DomainErrorResponse equality and copy`() {
        val resp = DomainErrorResponse(error = "Not found", requestId = "req-1")
        assertEquals(resp, resp.copy())
        assertEquals("Not found", resp.error)
        assertEquals("req-1", resp.requestId)
        assertNotEquals(resp, resp.copy(error = "Server error"))
    }

    @Test
    fun `HealthResponse equality and copy`() {
        val resp = HealthResponse(status = "ok")
        assertEquals(resp, resp.copy())
        assertEquals("ok", resp.status)
        assertNotEquals(resp, resp.copy(status = "degraded"))
        assertTrue(resp.status.isNotEmpty())
    }
}
