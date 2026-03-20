package com.kanbanvision.httpapi.routes

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteDtosContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given simulation request dtos when using defaults and copy then values remain consistent`() {
        val create = CreateSimulationRequest(organizationId = "org-1", wipLimit = 3, teamSize = 4)
        assertEquals(0L, create.seedValue)
        val createCopy = create.copy(seedValue = 99L)
        assertEquals(99L, createCopy.seedValue)
        assertEquals("org-1", create.component1())
        assertEquals(3, create.component2())
        assertEquals(4, create.component3())
        assertEquals(0L, create.component4())

        val decision = DecisionRequest(type = "move_item")
        assertTrue(decision.payload.isEmpty())
        val decisionCopy = decision.copy(payload = mapOf("cardId" to "c-1"))
        assertEquals("c-1", decisionCopy.payload["cardId"])

        val runDay = RunDayRequest()
        assertTrue(runDay.decisions.isEmpty())
        val runDayCopy = runDay.copy(decisions = listOf(decisionCopy))
        assertEquals(1, runDayCopy.decisions.size)
    }

    @Test
    fun `given auth request dto when serialized and deserialized then round trip preserves fields`() {
        val tokenRequest = IssueTokenRequest(subject = "dev-user", organizationId = "org-9")
        val tokenRequestJson = json.encodeToString(IssueTokenRequest.serializer(), tokenRequest)
        val decodedTokenRequest = json.decodeFromString(IssueTokenRequest.serializer(), tokenRequestJson)
        assertEquals("dev-user", decodedTokenRequest.subject)
        assertEquals("org-9", decodedTokenRequest.organizationId)
    }

    @Test
    fun `given simulation response dto when serialized and deserialized then round trip preserves fields`() {
        val state = SimulationStateResponse(currentDay = 5, wipLimit = 2, teamSize = 4, itemCount = 7)
        val response =
            SimulationResponse(
                simulationId = "sim-9",
                organizationId = "org-9",
                wipLimit = 2,
                teamSize = 4,
                seedValue = 123L,
                state = state,
            )
        val responseJson = json.encodeToString(SimulationResponse.serializer(), response)
        val decoded = json.decodeFromString(SimulationResponse.serializer(), responseJson)
        assertEquals("sim-9", decoded.simulationId)
        assertEquals(5, decoded.state.currentDay)
    }

    @Test
    fun `given daily snapshot response dto when serialized and deserialized then round trip preserves fields`() {
        val snapshot =
            DailySnapshotResponse(
                simulationId = "sim-9",
                day = 5,
                metrics = FlowMetricsResponse(throughput = 3, wipCount = 4, blockedCount = 1, avgAgingDays = 2.5),
                movements = listOf(MovementResponse(type = "MOVED", cardId = "card-1", day = 5, reason = "ok")),
            )
        val snapshotJson = json.encodeToString(DailySnapshotResponse.serializer(), snapshot)
        val snapshotDecoded = json.decodeFromString(DailySnapshotResponse.serializer(), snapshotJson)
        assertEquals("card-1", snapshotDecoded.movements.first().cardId)
    }

    @Test
    fun `given response dtos when copy and component methods are used then structural contract is stable`() {
        val created = SimulationCreatedResponse(simulationId = "sim-created")
        assertEquals("sim-created", created.component1())

        val metrics = FlowMetricsResponse(throughput = 8, wipCount = 3, blockedCount = 0, avgAgingDays = 1.2)
        val metricsCopy = metrics.copy(blockedCount = 2)
        assertEquals(2, metricsCopy.blockedCount)

        val movement = MovementResponse(type = "MOVED", cardId = "card-10", day = 2, reason = "progress")
        val movementCopy = movement.copy(reason = "done")
        assertEquals("done", movementCopy.reason)

        val tokenResponse = TokenResponse(token = "jwt-token")
        assertEquals("jwt-token", tokenResponse.component1())
    }
}
