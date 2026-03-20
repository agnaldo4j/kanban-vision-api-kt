package com.kanbanvision.usecases.scenario

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.queries.GetDailySnapshotQuery
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetDailySnapshotUseCaseTest {
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetDailySnapshotUseCase(snapshotRepository)

    private val scenarioId = "scenario-1"
    private val snapshot =
        DailySnapshot(
            scenarioId = scenarioId,
            day = SimulationDay(1),
            metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
            movements = emptyList(),
        )

    @Test
    fun `execute returns snapshot when found`() =
        runTest {
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns snapshot.right()

            val result = useCase.execute(GetDailySnapshotQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isRight())
            assertNotNull(result.getOrNull())
        }

    @Test
    fun `execute returns ScenarioNotFound when snapshot does not exist`() =
        runTest {
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns null.right()

            val result = useCase.execute(GetDailySnapshotQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isLeft())
            assertIs<DomainError.ScenarioNotFound>(result.leftOrNull())
        }

    @Test
    fun `execute returns PersistenceError when repository fails`() =
        runTest {
            coEvery { snapshotRepository.findByDay(scenarioId, SimulationDay(1)) } returns DomainError.PersistenceError("DB down").left()

            val result = useCase.execute(GetDailySnapshotQuery(scenarioId = scenarioId, day = 1))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }

    @Test
    fun `execute returns ValidationError when query is invalid`() =
        runTest {
            val result = useCase.execute(GetDailySnapshotQuery(scenarioId = "", day = 0))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
        }
}
