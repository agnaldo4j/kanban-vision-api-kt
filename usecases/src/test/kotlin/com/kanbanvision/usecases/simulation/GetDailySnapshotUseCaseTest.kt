package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetDailySnapshotUseCaseTest {
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetDailySnapshotUseCase(snapshotRepository)

    @Test
    fun `given existing snapshot when loading daily snapshot then use case returns snapshot`() =
        runTest {
            val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 2)
            coEvery { snapshotRepository.findByDay("sim-1", SimulationDay(2)) } returns snapshot.right()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 2))

            assertTrue(result.isRight())
            assertEquals(snapshot.id, result.getOrNull()?.id)
            coVerify(exactly = 1) { snapshotRepository.findByDay("sim-1", SimulationDay(2)) }
        }

    @Test
    fun `given missing snapshot when loading daily snapshot then simulation not found is returned`() =
        runTest {
            coEvery { snapshotRepository.findByDay("sim-1", SimulationDay(2)) } returns null.right()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 2))

            assertTrue(result.isLeft())
            assertIs<DomainError.SimulationNotFound>(result.leftOrNull())
            coVerify(exactly = 1) { snapshotRepository.findByDay("sim-1", SimulationDay(2)) }
        }

    @Test
    fun `given invalid query when loading daily snapshot then validation error is returned`() =
        runTest {
            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "", day = 0))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { snapshotRepository.findByDay(any(), SimulationDay(1)) }
            confirmVerified(snapshotRepository)
        }

    @Test
    fun `given repository failure when loading daily snapshot then persistence error is propagated`() =
        runTest {
            coEvery {
                snapshotRepository.findByDay("sim-1", SimulationDay(1))
            } returns DomainError.PersistenceError("db unavailable").left()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 1))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
