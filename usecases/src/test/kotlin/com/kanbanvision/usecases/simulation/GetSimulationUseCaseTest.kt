package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.domain.errors.SimulationError
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetSimulationUseCaseTest {
    private val simulationRepository = mockk<SimulationRepository>()
    private val useCase = GetSimulationUseCase(simulationRepository)

    @Test
    fun `given existing simulation id when loading simulation then use case returns the aggregate`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", organizationId = "org-1")
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()

            val result = useCase.execute(GetSimulationQuery(simulationId = "sim-1", callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
            coVerify(exactly = 1) { simulationRepository.findById(SimulationId("sim-1")) }
        }

    @Test
    fun `given blank simulation id when loading simulation then validation error is returned`() =
        runTest {
            val result = useCase.execute(GetSimulationQuery(simulationId = "", callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<CommonError.ValidationError>(result.leftOrNull())
            coVerify(exactly = 0) { simulationRepository.findById(any()) }
        }

    @Test
    fun `given unknown simulation id when loading simulation then not found error is propagated`() =
        runTest {
            coEvery { simulationRepository.findById(SimulationId("sim-missing")) } returns
                SimulationError.SimulationNotFound("sim-missing").left()

            val result = useCase.execute(GetSimulationQuery(simulationId = "sim-missing", callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<SimulationError.SimulationNotFound>(result.leftOrNull())
            coVerify(exactly = 1) { simulationRepository.findById(SimulationId("sim-missing")) }
        }

    @Test
    fun `given simulation of another organization when loading simulation then forbidden is returned`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", organizationId = "org-owner")
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()

            val result = useCase.execute(GetSimulationQuery(simulationId = "sim-1", callerOrganizationId = "org-attacker"))

            assertTrue(result.isLeft())
            assertIs<CommonError.Forbidden>(result.leftOrNull())
            coVerify(exactly = 1) { simulationRepository.findById(SimulationId("sim-1")) }
        }
}
