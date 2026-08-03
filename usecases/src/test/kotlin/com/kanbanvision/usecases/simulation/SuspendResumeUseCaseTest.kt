package com.kanbanvision.usecases.simulation

import arrow.core.right
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationResult
import com.kanbanvision.usecases.ports.EventPublisherPort
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.OrganizationRepository
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.commands.CreateSimulationCommand
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationCfdQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cada use case percorrido com colaboradores que SUSPENDEM DE VERDADE.
 *
 * Todo mock do módulo usa `coEvery { … } returns x`, que devolve na hora: a continuation nunca é
 * salva e o caminho de resume — `invokeSuspend` e `throwOnFailure` — não executa em teste algum.
 * Em produção ele executa sempre, porque `dbQuery` faz `withContext(Dispatchers.IO)`. Medido: 54
 * mutantes ficavam `NO_COVERAGE` só por isso, e o primeiro teste desta forma matou os 8 dos três
 * arquivos que tocou.
 *
 * `delay` basta e é o mínimo verificado: sob `runTest` ele pula o tempo virtual mas a suspensão é real,
 * e medi que trocar de dispatcher junto não mata nenhum mutante a mais. O que não vale é o mock que
 * resolve na mesma pilha — `coEvery { … } returns x`.
 */
class SuspendResumeUseCaseTest {
    private val simulationRepository = mockk<SimulationRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val organizationRepository = mockk<OrganizationRepository>()
    private val simulationEngine = mockk<SimulationEnginePort>()
    private val publisher = mockk<EventPublisherPort>(relaxed = true)
    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    @Test
    fun `given a repository that really suspends when getting a simulation then the resumed path returns the aggregate`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", organizationId = "org-1")
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } coAnswers { suspending(simulation.right()) }

            val result =
                GetSimulationUseCase(simulationRepository)
                    .execute(GetSimulationQuery(simulationId = "sim-1", callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
        }

    @Test
    fun `given repositories that really suspend when listing simulations then the resumed path returns the page`() =
        runTest {
            val simulations = listOf(fixtureSimulation(id = "sim-1"))
            coEvery { simulationRepository.findAll("org-1", 1, 20) } coAnswers { suspending(simulations.right()) }
            coEvery { simulationRepository.countByOrganization("org-1") } coAnswers { suspending(1L.right()) }

            val result = ListSimulationsUseCase(simulationRepository).execute(ListSimulationsQuery(organizationId = "org-1"))

            assertTrue(result.isRight())
        }

    @Test
    fun `given repositories that really suspend when getting a daily snapshot then the resumed path returns it`() =
        runTest {
            givenOwnedSimulationSuspends()
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1)) } coAnswers {
                suspending(fixtureSnapshot().right())
            }

            val result =
                GetDailySnapshotUseCase(simulationRepository, snapshotRepository)
                    .execute(GetDailySnapshotQuery(simulationId = "sim-1", day = 1, callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
        }

    @Test
    fun `given repositories that really suspend when getting simulation days then the resumed path returns them`() =
        runTest {
            givenOwnedSimulationSuspends()
            givenSnapshotsSuspend()

            val result =
                GetSimulationDaysUseCase(simulationRepository, snapshotRepository)
                    .execute(GetSimulationDaysQuery(simulationId = "sim-1", callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
        }

    @Test
    fun `given repositories that really suspend when getting the cfd then the resumed path returns it`() =
        runTest {
            givenOwnedSimulationSuspends()
            givenSnapshotsSuspend()

            val result =
                GetSimulationCfdUseCase(simulationRepository, snapshotRepository)
                    .execute(GetSimulationCfdQuery(simulationId = "sim-1", callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
        }

    @Test
    fun `given repositories that really suspend when creating a simulation then the resumed path persists it`() =
        runTest {
            coEvery { organizationRepository.findById("org-1") } coAnswers { suspending(fixtureOrganization().right()) }
            coEvery { simulationRepository.save(any()) } coAnswers { suspending(firstArg<Simulation>().right()) }

            val result =
                CreateSimulationUseCase(organizationRepository, simulationRepository, publisher, clock)
                    .execute(CreateSimulationCommand("org-1", wipLimit = 2, teamSize = 2, seedValue = 7L))

            assertTrue(result.isRight())
        }

    @Test
    fun `given repositories that really suspend when running a day then the resumed path persists the snapshot`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", day = 1)
            val advanced = fixtureSimulation(id = "sim-1", day = 2)
            val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 1)

            coEvery { simulationRepository.findById(SimulationId("sim-1")) } coAnswers { suspending(simulation.right()) }
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1)) } coAnswers { suspending(null.right()) }
            coEvery { simulationEngine.runDay(simulation, any(), any(), any()) } returns
                SimulationResult(simulation = advanced, snapshot = snapshot)
            coEvery { simulationRepository.save(advanced) } coAnswers { suspending(advanced.right()) }
            coEvery { snapshotRepository.save(snapshot) } coAnswers { suspending(snapshot.right()) }

            val result =
                RunDayUseCase(simulationRepository, snapshotRepository, simulationEngine, publisher, clock)
                    .execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList(), callerOrganizationId = "org-1"))

            assertTrue(result.isRight())
        }

    private fun givenOwnedSimulationSuspends() {
        coEvery { simulationRepository.findById(SimulationId("sim-1")) } coAnswers {
            suspending(fixtureSimulation(id = "sim-1", organizationId = "org-1").right())
        }
    }

    private fun givenSnapshotsSuspend() {
        coEvery { snapshotRepository.findAllBySimulation(SimulationId("sim-1")) } coAnswers {
            suspending(listOf(fixtureSnapshot()).right())
        }
    }

    private suspend fun <T> suspending(value: T): T {
        delay(1)
        return value
    }
}
