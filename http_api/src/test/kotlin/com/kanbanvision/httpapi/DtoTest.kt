package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.routes.BoardResponse
import com.kanbanvision.httpapi.routes.CardResponse
import com.kanbanvision.httpapi.routes.CreateBoardRequest
import com.kanbanvision.httpapi.routes.CreateCardRequest
import com.kanbanvision.httpapi.routes.MoveCardRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DtoTest {
    @Test
    fun `CreateBoardRequest equality and copy`() {
        val req = CreateBoardRequest("My Board")
        assertEquals(req, req.copy())
        assertEquals("My Board", req.name)
        assertNotEquals(req, CreateBoardRequest("Other"))
    }

    @Test
    fun `CreateCardRequest equality and copy`() {
        val req = CreateCardRequest(columnId = "col-1", title = "Task", description = "Desc")
        assertEquals(req, req.copy())
        assertEquals("col-1", req.columnId)
        assertEquals("Task", req.title)
        assertNotEquals(req, CreateCardRequest("col-2", "Task"))
    }

    @Test
    fun `MoveCardRequest equality and copy`() {
        val req = MoveCardRequest(columnId = "col-1", position = 2)
        assertEquals(req, req.copy())
        assertEquals("col-1", req.columnId)
        assertEquals(2, req.position)
        assertNotEquals(req, MoveCardRequest("col-2", 2))
    }

    @Test
    fun `BoardResponse equality and copy`() {
        val resp = BoardResponse(id = "board-1", name = "Sprint")
        assertEquals(resp, resp.copy())
        assertEquals("board-1", resp.id)
        assertNotEquals(resp, BoardResponse("board-2", "Sprint"))
    }

    @Test
    fun `CardResponse equality and copy`() {
        val resp = CardResponse(id = "c1", columnId = "col1", title = "Task", description = "Desc", position = 0)
        assertEquals(resp, resp.copy())
        assertEquals("c1", resp.id)
        assertNotEquals(resp, resp.copy(id = "c2"))
    }
}
