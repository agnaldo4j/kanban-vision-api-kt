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
    fun `given summary and list response dtos when copy and component methods are used then structural contract is stable`() {
        val summary = SimulationSummaryResponse(id = "s1", name = "Sim", status = "DRAFT", currentDay = 3)
        assertEquals("s1", summary.id)
        assertEquals("s1", summary.component1())
        assertEquals("Sim", summary.component2())
        assertEquals("DRAFT", summary.component3())
        assertEquals(3, summary.component4())
        assertEquals(3, summary.currentDay)
        assertEquals(summary, summary.copy())
        val summaryCopy = summary.copy(currentDay = 5)
        assertEquals(5, summaryCopy.currentDay)

        val listResp = SimulationListResponse(data = listOf(summary), page = 2, size = 10, total = 25L)
        assertEquals(1, listResp.data.size)
        assertEquals(2, listResp.page)
        assertEquals(10, listResp.size)
        assertEquals(25L, listResp.total)
        val listJson = json.encodeToString(SimulationListResponse.serializer(), listResp)
        val listDecoded = json.decodeFromString(SimulationListResponse.serializer(), listJson)
        assertEquals(25L, listDecoded.total)

        val dayMetrics = DayMetricsResponse(day = 7, throughput = 5, wipCount = 3, blockedCount = 1, avgAgingDays = 2.5)
        assertEquals(7, dayMetrics.day)
        assertEquals(5, dayMetrics.throughput)
        assertEquals(3, dayMetrics.wipCount)
        assertEquals(1, dayMetrics.blockedCount)
        assertEquals(2.5, dayMetrics.avgAgingDays)
        val dayMetricsCopy = dayMetrics.copy(throughput = 9)
        assertEquals(9, dayMetricsCopy.throughput)
    }

    @Test
    fun `given days and cfd response dtos when serialized and component methods are used then structural contract is stable`() {
        val dayMetrics = DayMetricsResponse(day = 7, throughput = 5, wipCount = 3, blockedCount = 1, avgAgingDays = 2.5)
        val daysResp = SimulationDaysResponse(simulationId = "sim-4", days = listOf(dayMetrics))
        assertEquals("sim-4", daysResp.simulationId)
        assertEquals(1, daysResp.days.size)
        val daysJson = json.encodeToString(SimulationDaysResponse.serializer(), daysResp)
        val daysDecoded = json.decodeFromString(SimulationDaysResponse.serializer(), daysJson)
        assertEquals("sim-4", daysDecoded.simulationId)

        val cfdPoint = CfdDataPointResponse(day = 4, throughputCumulative = 12, wipCount = 2, blockedCount = 0)
        assertEquals(4, cfdPoint.day)
        assertEquals(4, cfdPoint.component1())
        assertEquals(12, cfdPoint.component2())
        assertEquals(2, cfdPoint.component3())
        assertEquals(0, cfdPoint.component4())
        assertEquals(12, cfdPoint.throughputCumulative)
        val cfdPointCopy = cfdPoint.copy(throughputCumulative = 15)
        assertEquals(15, cfdPointCopy.throughputCumulative)
        assertEquals(cfdPoint, cfdPoint.copy())
        assertTrue(cfdPoint.toString().contains("12"))

        val cfdResp = SimulationCfdResponse(simulationId = "sim-5", series = listOf(cfdPoint))
        assertEquals("sim-5", cfdResp.simulationId)
        assertEquals("sim-5", cfdResp.component1())
        val cfdJson = json.encodeToString(SimulationCfdResponse.serializer(), cfdResp)
        val cfdDecoded = json.decodeFromString(SimulationCfdResponse.serializer(), cfdJson)
        assertEquals("sim-5", cfdDecoded.simulationId)
        assertEquals(12, cfdDecoded.series.first().throughputCumulative)
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
