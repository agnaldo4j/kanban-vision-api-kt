package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationCfdQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetSimulationCfdUseCaseTest {
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetSimulationCfdUseCase(snapshotRepository)

    @Test
    fun `given simulation with snapshots when building cfd then series accumulates throughput correctly`() =
        runTest {
            val snapshots =
                listOf(
                    fixtureSnapshot(
                        day = 1,
                    ).copy(metrics = FlowMetrics(throughput = 3, wipCount = 5, blockedCount = 1, avgAgingDays = 1.0)),
                    fixtureSnapshot(
                        day = 2,
                    ).copy(metrics = FlowMetrics(throughput = 2, wipCount = 4, blockedCount = 0, avgAgingDays = 1.5)),
                )
            coEvery { snapshotRepository.findAllBySimulation("sim-1") } returns snapshots.right()

            val result = useCase.execute(GetSimulationCfdQuery(simulationId = "sim-1"))

            assertTrue(result.isRight())
            val cfd = result.getOrNull()!!
            assertEquals("sim-1", cfd.simulationId)
            assertEquals(2, cfd.series.size)
            assertEquals(3, cfd.series[0].throughputCumulative)
            assertEquals(5, cfd.series[0].wipCount)
            assertEquals(1, cfd.series[0].blockedCount)
            assertEquals(5, cfd.series[1].throughputCumulative) // 3 + 2 = 5 accumulated
            assertEquals(4, cfd.series[1].wipCount)
            coVerify(exactly = 1) { snapshotRepository.findAllBySimulation("sim-1") }
        }

    @Test
    fun `given simulation with no snapshots when building cfd then empty series is returned`() =
        runTest {
            coEvery { snapshotRepository.findAllBySimulation("sim-empty") } returns
                emptyList<DailySnapshot>().right()

            val result = useCase.execute(GetSimulationCfdQuery(simulationId = "sim-empty"))

            assertTrue(result.isRight())
            val cfd = result.getOrNull()!!
            assertEquals("sim-empty", cfd.simulationId)
            assertTrue(cfd.series.isEmpty())
        }

    @Test
    fun `given unordered snapshots when building cfd then series is sorted by day`() =
        runTest {
            val snapshots =
                listOf(
                    fixtureSnapshot(
                        day = 3,
                    ).copy(metrics = FlowMetrics(throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 0.0)),
                    fixtureSnapshot(
                        day = 1,
                    ).copy(metrics = FlowMetrics(throughput = 2, wipCount = 3, blockedCount = 1, avgAgingDays = 0.5)),
                    fixtureSnapshot(
                        day = 2,
                    ).copy(metrics = FlowMetrics(throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 1.0)),
                )
            coEvery { snapshotRepository.findAllBySimulation("sim-1") } returns snapshots.right()

            val result = useCase.execute(GetSimulationCfdQuery(simulationId = "sim-1"))

            assertTrue(result.isRight())
            val series = result.getOrNull()!!.series
            assertEquals(1, series[0].day)
            assertEquals(2, series[1].day)
            assertEquals(3, series[2].day)
            assertEquals(2, series[0].throughputCumulative)
            assertEquals(3, series[1].throughputCumulative)
            assertEquals(4, series[2].throughputCumulative)
        }

    @Test
    fun `given blank simulation id when building cfd then validation error is returned`() =
        runTest {
            val result = useCase.execute(GetSimulationCfdQuery(simulationId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.findAllBySimulation(any()) }
        }

    @Test
    fun `given persistence error when building cfd then error is propagated`() =
        runTest {
            coEvery { snapshotRepository.findAllBySimulation("sim-1") } returns
                DomainError.PersistenceError("db error").left()

            val result = useCase.execute(GetSimulationCfdQuery(simulationId = "sim-1"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
