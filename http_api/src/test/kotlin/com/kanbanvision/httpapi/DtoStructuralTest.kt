package com.kanbanvision.httpapi

import com.kanbanvision.domain.model.team.AbilityName
import com.kanbanvision.httpapi.dtos.DomainErrorResponse
import com.kanbanvision.httpapi.dtos.ValidationErrorResponse
import com.kanbanvision.httpapi.routes.ColumnResponse
import com.kanbanvision.httpapi.routes.CreateColumnRequest
import com.kanbanvision.httpapi.routes.CreateStepRequest
import com.kanbanvision.httpapi.routes.HealthResponse
import com.kanbanvision.httpapi.routes.IssueTokenRequest
import com.kanbanvision.httpapi.routes.StepResponse
import com.kanbanvision.httpapi.routes.TokenResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DtoStructuralTest {
    @Test
    fun `CreateColumnRequest equality and copy`() {
        val req = CreateColumnRequest(boardId = "b1", name = "To Do", requiredAbility = AbilityName.PRODUCT_MANAGER)
        assertEquals(req, req.copy())
        assertEquals("b1", req.boardId)
        assertEquals("To Do", req.name)
        assertEquals(AbilityName.PRODUCT_MANAGER, req.requiredAbility)
        assertNotEquals(req, req.copy(boardId = "b2"))
    }

    @Test
    fun `ColumnResponse equality and copy`() {
        val resp =
            ColumnResponse(
                id = "col-1",
                boardId = "b-1",
                name = "In Progress",
                position = 1,
                requiredAbility = AbilityName.DEVELOPER,
            )
        assertEquals(resp, resp.copy())
        assertEquals("col-1", resp.id)
        assertEquals("b-1", resp.boardId)
        assertEquals("In Progress", resp.name)
        assertEquals(1, resp.position)
        assertEquals(AbilityName.DEVELOPER, resp.requiredAbility)
        assertNotEquals(resp, resp.copy(id = "col-2"))
    }

    @Test
    fun `CreateStepRequest equality and copy`() {
        val req = CreateStepRequest(boardId = "b-1", name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
        assertEquals(req, req.copy())
        assertEquals("b-1", req.boardId)
        assertEquals("Analysis", req.name)
        assertEquals(AbilityName.PRODUCT_MANAGER, req.requiredAbility)
        assertNotEquals(req, req.copy(name = "Development"))
    }

    @Test
    fun `StepResponse equality and copy`() {
        val resp =
            StepResponse(
                id = "s-1",
                boardId = "b-1",
                name = "Test",
                position = 2,
                requiredAbility = AbilityName.TESTER,
            )
        assertEquals(resp, resp.copy())
        assertEquals("s-1", resp.id)
        assertEquals("b-1", resp.boardId)
        assertEquals("Test", resp.name)
        assertEquals(2, resp.position)
        assertEquals(AbilityName.TESTER, resp.requiredAbility)
        assertNotEquals(resp, resp.copy(id = "s-2"))
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

    @Test
    fun `IssueTokenRequest equality and copy`() {
        val req = IssueTokenRequest(subject = "user-1", tenantId = "tenant-1")
        assertEquals(req, req.copy())
        assertEquals("user-1", req.subject)
        assertEquals("tenant-1", req.tenantId)
        assertNotEquals(req, req.copy(subject = "user-2"))
    }

    @Test
    fun `TokenResponse equality and copy`() {
        val resp = TokenResponse(token = "jwt-token")
        assertEquals(resp, resp.copy())
        assertEquals("jwt-token", resp.token)
        assertNotEquals(resp, resp.copy(token = "other-token"))
    }
}
