package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetSimulationDaysUseCaseTest {
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetSimulationDaysUseCase(snapshotRepository)

    @Test
    fun `given simulation with snapshots when fetching days then all snapshots are returned sorted by day`() =
        runTest {
            val snapshots = listOf(fixtureSnapshot(day = 3), fixtureSnapshot(day = 1), fixtureSnapshot(day = 2))
            coEvery { snapshotRepository.findAllBySimulation("sim-1") } returns snapshots.right()

            val result = useCase.execute(GetSimulationDaysQuery(simulationId = "sim-1"))

            assertTrue(result.isRight())
            val days = result.getOrNull()!!
            assertEquals(3, days.size)
            assertEquals(1, days[0].day.value)
            assertEquals(2, days[1].day.value)
            assertEquals(3, days[2].day.value)
            coVerify(exactly = 1) { snapshotRepository.findAllBySimulation("sim-1") }
        }

    @Test
    fun `given simulation with no snapshots when fetching days then empty list is returned`() =
        runTest {
            coEvery { snapshotRepository.findAllBySimulation("sim-empty") } returns
                emptyList<DailySnapshot>().right()

            val result = useCase.execute(GetSimulationDaysQuery(simulationId = "sim-empty"))

            assertTrue(result.isRight())
            assertTrue(result.getOrNull()!!.isEmpty())
        }

    @Test
    fun `given blank simulation id when fetching days then validation error is returned`() =
        runTest {
            val result = useCase.execute(GetSimulationDaysQuery(simulationId = ""))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.findAllBySimulation(any()) }
        }

    @Test
    fun `given persistence error when fetching days then error is propagated`() =
        runTest {
            coEvery { snapshotRepository.findAllBySimulation("sim-1") } returns
                DomainError.PersistenceError("db error").left()

            val result = useCase.execute(GetSimulationDaysQuery(simulationId = "sim-1"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
