package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.model.simulation.SimulationError
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * O seam de autorização passa a ser testável DIRETAMENTE (GAP-DW). Antes, a regra só existia replicada
 * dentro de 5 use cases e só era exercitada através deles; aqui ela é exercida sozinha, o que é a moeda
 * de troca do refactor: um ponto único precisa de um teste próprio, não só dos 5 herdados.
 */
class LoadOwnedSimulationTest {
    private val simulationRepository = mockk<SimulationRepository>()

    @Test
    fun `given simulation of the caller organization when loading then it is returned`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", organizationId = "org-owner")
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()

            val result = either { loadOwnedSimulation(simulationRepository, SimulationId("sim-1"), "org-owner") }

            assertEquals(simulation, result.getOrNull())
        }

    @Test
    fun `given simulation of another organization when loading then forbidden is returned`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", organizationId = "org-owner")
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()

            val result = either { loadOwnedSimulation(simulationRepository, SimulationId("sim-1"), "org-attacker") }

            assertIs<CommonError.Forbidden>(result.leftOrNull())
        }

    @Test
    fun `given repository failure when loading then the error is propagated unchanged`() =
        runTest {
            // O guard não pode mascarar a falha de carga como Forbidden: quem não existe é NotFound,
            // quem não é seu é Forbidden. Confundir os dois vaza existência (security.md, A01).
            coEvery {
                simulationRepository.findById(SimulationId("sim-1"))
            } returns SimulationError.SimulationNotFound("sim-1").left()

            val result = either { loadOwnedSimulation(simulationRepository, SimulationId("sim-1"), "org-owner") }

            assertIs<SimulationError.SimulationNotFound>(result.leftOrNull())
        }
}
