package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.metrics.FlowMetrics
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.queries.GetFlowMetricsRangeQuery
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetFlowMetricsRangeUseCaseTest {
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetFlowMetricsRangeUseCase(snapshotRepository)

    private val scenarioId = ScenarioId("scenario-1")

    private fun snapshot(
        day: Int,
        throughput: Int,
    ) = DailySnapshot(
        scenarioId = scenarioId,
        day = SimulationDay(day),
        metrics = FlowMetrics(throughput = throughput, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
        movements = emptyList(),
    )

    @Test
    fun `execute returns metrics for all days in range`() =
        runTest {
            val snapshots = listOf(snapshot(1, 1), snapshot(2, 2), snapshot(3, 3))
            coEvery { snapshotRepository.findAllByScenario(scenarioId) } returns snapshots.right()

            val result = useCase.execute(GetFlowMetricsRangeQuery(scenarioId = scenarioId.value, fromDay = 1, toDay = 3))

            assertTrue(result.isRight())
            val metrics = result.getOrNull()!!
            assertEquals(3, metrics.size)
            assertEquals(1, metrics[0].day)
            assertEquals(2, metrics[1].day)
            assertEquals(3, metrics[2].day)
        }

    @Test
    fun `execute filters snapshots outside the requested range`() =
        runTest {
            val snapshots = listOf(snapshot(1, 1), snapshot(2, 2), snapshot(3, 3), snapshot(4, 4), snapshot(5, 5))
            coEvery { snapshotRepository.findAllByScenario(scenarioId) } returns snapshots.right()

            val result = useCase.execute(GetFlowMetricsRangeQuery(scenarioId = scenarioId.value, fromDay = 2, toDay = 4))

            assertTrue(result.isRight())
            val metrics = result.getOrNull()!!
            assertEquals(3, metrics.size)
            assertEquals(2, metrics.first().day)
            assertEquals(4, metrics.last().day)
        }

    @Test
    fun `execute returns sorted metrics when snapshots are unordered`() =
        runTest {
            val snapshots = listOf(snapshot(3, 3), snapshot(1, 1), snapshot(2, 2))
            coEvery { snapshotRepository.findAllByScenario(scenarioId) } returns snapshots.right()

            val result = useCase.execute(GetFlowMetricsRangeQuery(scenarioId = scenarioId.value, fromDay = 1, toDay = 3))

            assertTrue(result.isRight())
            val metrics = result.getOrNull()!!
            assertEquals(listOf(1, 2, 3), metrics.map { it.day })
        }

    @Test
    fun `execute returns empty list when no snapshots match range`() =
        runTest {
            coEvery { snapshotRepository.findAllByScenario(scenarioId) } returns emptyList<DailySnapshot>().right()

            val result = useCase.execute(GetFlowMetricsRangeQuery(scenarioId = scenarioId.value, fromDay = 1, toDay = 5))

            assertTrue(result.isRight())
            assertEquals(emptyList(), result.getOrNull())
        }

    @Test
    fun `execute returns PersistenceError when repository fails`() =
        runTest {
            coEvery { snapshotRepository.findAllByScenario(scenarioId) } returns
                DomainError.PersistenceError("DB down").left()

            val result = useCase.execute(GetFlowMetricsRangeQuery(scenarioId = scenarioId.value, fromDay = 1, toDay = 3))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `execute returns ValidationError when query is invalid`() =
        runTest {
            val result = useCase.execute(GetFlowMetricsRangeQuery(scenarioId = "", fromDay = 0, toDay = -1))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }
}
