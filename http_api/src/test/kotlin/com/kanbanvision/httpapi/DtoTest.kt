package com.kanbanvision.httpapi

import com.kanbanvision.httpapi.routes.BoardResponse
import com.kanbanvision.httpapi.routes.CardResponse
import com.kanbanvision.httpapi.routes.CreateBoardRequest
import com.kanbanvision.httpapi.routes.CreateCardRequest
import com.kanbanvision.httpapi.routes.CreateScenarioRequest
import com.kanbanvision.httpapi.routes.DailyMetricsResponse
import com.kanbanvision.httpapi.routes.DailySnapshotResponse
import com.kanbanvision.httpapi.routes.DecisionRequest
import com.kanbanvision.httpapi.routes.FlowMetricsResponse
import com.kanbanvision.httpapi.routes.MoveCardRequest
import com.kanbanvision.httpapi.routes.MovementResponse
import com.kanbanvision.httpapi.routes.RunDayRequest
import com.kanbanvision.httpapi.routes.ScenarioCreatedResponse
import com.kanbanvision.httpapi.routes.ScenarioResponse
import com.kanbanvision.httpapi.routes.SimulationStateResponse
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

    @Test
    fun `CreateScenarioRequest equality and copy`() {
        val req = CreateScenarioRequest(tenantId = "t1", wipLimit = 3, teamSize = 2, seedValue = 42L)
        assertEquals(req, req.copy())
        assertEquals("t1", req.tenantId)
        assertNotEquals(req, req.copy(tenantId = "t2"))
    }

    @Test
    fun `DecisionRequest equality and copy`() {
        val req = DecisionRequest(type = "MOVE_ITEM", payload = mapOf("workItemId" to "w1"))
        assertEquals(req, req.copy())
        assertEquals("MOVE_ITEM", req.type)
        assertNotEquals(req, req.copy(type = "BLOCK_ITEM"))
    }

    @Test
    fun `RunDayRequest equality and copy`() {
        val req = RunDayRequest(decisions = listOf(DecisionRequest(type = "MOVE_ITEM")))
        assertEquals(req, req.copy())
        assertEquals(1, req.decisions.size)
        assertNotEquals(req, RunDayRequest())
    }

    @Test
    fun `ScenarioCreatedResponse equality and copy`() {
        val resp = ScenarioCreatedResponse(scenarioId = "s1")
        assertEquals(resp, resp.copy())
        assertEquals("s1", resp.scenarioId)
        assertNotEquals(resp, resp.copy(scenarioId = "s2"))
    }

    @Test
    fun `SimulationStateResponse equality and copy`() {
        val resp = SimulationStateResponse(currentDay = 1, wipLimit = 3, teamSize = 2, itemCount = 5)
        assertEquals(resp, resp.copy())
        assertEquals(1, resp.currentDay)
        assertNotEquals(resp, resp.copy(currentDay = 2))
    }

    @Test
    fun `ScenarioResponse equality and copy`() {
        val stateResp = SimulationStateResponse(currentDay = 1, wipLimit = 3, teamSize = 2, itemCount = 0)
        val resp =
            ScenarioResponse(
                scenarioId = "s1",
                tenantId = "t1",
                wipLimit = 3,
                teamSize = 2,
                seedValue = 42L,
                state = stateResp,
            )
        assertEquals(resp, resp.copy())
        assertEquals("s1", resp.scenarioId)
        assertNotEquals(resp, resp.copy(scenarioId = "s2"))
    }

    @Test
    fun `FlowMetricsResponse equality and copy`() {
        val resp = FlowMetricsResponse(throughput = 2, wipCount = 3, blockedCount = 1, avgAgingDays = 1.5)
        assertEquals(resp, resp.copy())
        assertEquals(2, resp.throughput)
        assertNotEquals(resp, resp.copy(throughput = 5))
    }

    @Test
    fun `MovementResponse equality and copy`() {
        val resp = MovementResponse(type = "MOVED", workItemId = "w1", day = 1, reason = "WIP available")
        assertEquals(resp, resp.copy())
        assertEquals("MOVED", resp.type)
        assertNotEquals(resp, resp.copy(type = "BLOCKED"))
    }

    @Test
    fun `DailySnapshotResponse equality and copy`() {
        val metrics = FlowMetricsResponse(throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 0.5)
        val resp = DailySnapshotResponse(scenarioId = "s1", day = 1, metrics = metrics, movements = emptyList())
        assertEquals(resp, resp.copy())
        assertEquals("s1", resp.scenarioId)
        assertNotEquals(resp, resp.copy(scenarioId = "s2"))
    }

    @Test
    fun `DailyMetricsResponse equality and copy`() {
        val resp = DailyMetricsResponse(day = 1, throughput = 2, wipCount = 3, blockedCount = 0, avgAgingDays = 1.5)
        assertEquals(resp, resp.copy())
        assertEquals(1, resp.day)
        assertNotEquals(resp, resp.copy(day = 2))
    }
}
