package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetDailySnapshotUseCaseTest {
    private val simulationRepository = mockk<SimulationRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val useCase = GetDailySnapshotUseCase(simulationRepository, snapshotRepository)

    @Test
    fun `given existing snapshot when loading daily snapshot then use case returns snapshot`() =
        runTest {
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns
                fixtureSimulation(id = "sim-1", organizationId = "org-1").right()
            val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 2)
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(2)) } returns snapshot.right()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 2, callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
            assertEquals(snapshot.id, result.getOrNull()?.id)
            coVerify(exactly = 1) { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(2)) }
        }

    @Test
    fun `given missing snapshot when loading daily snapshot then simulation not found is returned`() =
        runTest {
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns
                fixtureSimulation(id = "sim-1", organizationId = "org-1").right()
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(2)) } returns null.right()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 2, callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<DomainError.SimulationNotFound>(result.leftOrNull())
            coVerify(exactly = 1) { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(2)) }
        }

    @Test
    fun `given invalid query when loading daily snapshot then validation error is returned`() =
        runTest {
            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "", day = 0, callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())
            coVerify { simulationRepository wasNot Called }
            coVerify { snapshotRepository wasNot Called }
        }

    @Test
    fun `given snapshot of another organization when loading daily snapshot then forbidden is returned`() =
        runTest {
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns
                fixtureSimulation(id = "sim-1", organizationId = "org-owner").right()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 2, callerOrganizationId = "org-attacker"))

            assertTrue(result.isLeft())
            assertIs<DomainError.Forbidden>(result.leftOrNull())
            coVerify { snapshotRepository wasNot Called }
        }

    @Test
    fun `given repository failure when loading daily snapshot then persistence error is propagated`() =
        runTest {
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns
                fixtureSimulation(id = "sim-1", organizationId = "org-1").right()
            coEvery {
                snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1))
            } returns DomainError.PersistenceError("db unavailable").left()

            val result = useCase.execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 1, callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())
        }
}
