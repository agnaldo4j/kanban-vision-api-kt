package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunDayUseCaseTest {
    private val simulationRepository = mockk<SimulationRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val simulationEngine = mockk<SimulationEnginePort>()
    private val useCase = RunDayUseCase(simulationRepository, snapshotRepository, simulationEngine)

    @Test
    fun `given already executed day when running simulation day then conflict error is returned`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", day = 3)
            val existingSnapshot = fixtureSnapshot(simulationId = "sim-1", day = 3)

            coEvery { simulationRepository.findById("sim-1") } returns simulation.right()
            coEvery { snapshotRepository.findByDay("sim-1", SimulationDay(3)) } returns existingSnapshot.right()

            val result = useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList()))

            assertTrue(result.isLeft())
            val error = result.leftOrNull()
            assertIs<DomainError.DayAlreadyExecuted>(error)
            assertEquals(3, error.day)

            coVerify(exactly = 0) { simulationEngine.runDay(any(), any(), any()) }
            coVerify(exactly = 0) { simulationRepository.save(any()) }
            coVerify(exactly = 0) { snapshotRepository.save(any()) }
        }

    @Test
    fun `given valid command and empty day when running simulation day then result is persisted and returned`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", day = 1)
            val updatedSimulation = fixtureSimulation(id = "sim-1", day = 2)
            val snapshot = fixtureSnapshot(simulationId = "sim-1", day = 1)

            coEvery { simulationRepository.findById("sim-1") } returns simulation.right()
            coEvery { snapshotRepository.findByDay("sim-1", SimulationDay(1)) } returns null.right()
            coEvery {
                simulationEngine.runDay(simulation = simulation, decisions = any(), seed = simulation.scenario.rules.seedValue)
            } returns SimulationResult(simulation = updatedSimulation, snapshot = snapshot)
            coEvery { simulationRepository.save(updatedSimulation) } returns updatedSimulation.right()
            coEvery { snapshotRepository.save(snapshot) } returns snapshot.right()

            val result = useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = listOf(Decision.move("card-1"))))

            assertTrue(result.isRight())
            assertEquals(snapshot.id, result.getOrNull()?.id)

            coVerify(exactly = 1) { simulationRepository.findById("sim-1") }
            coVerify(exactly = 1) { snapshotRepository.findByDay("sim-1", SimulationDay(1)) }
            coVerify(exactly = 1) { simulationEngine.runDay(simulation, any(), simulation.scenario.rules.seedValue) }
            coVerify(exactly = 1) { simulationRepository.save(updatedSimulation) }
            coVerify(exactly = 1) { snapshotRepository.save(snapshot) }
        }

    @Test
    fun `given blank simulation id when running simulation day then validation error is returned`() =
        runTest {
            val result = useCase.execute(RunDayCommand(simulationId = "", decisions = emptyList()))

            assertTrue(result.isLeft())
            assertIs<DomainError.ValidationError>(result.leftOrNull())

            coVerify(exactly = 0) { simulationRepository.findById(any()) }
        }

    @Test
    fun `given repository failure when loading simulation then error is propagated without side effects`() =
        runTest {
            coEvery { simulationRepository.findById("sim-1") } returns DomainError.PersistenceError("db unavailable").left()

            val result = useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList()))

            assertTrue(result.isLeft())
            assertIs<DomainError.PersistenceError>(result.leftOrNull())

            coVerify(exactly = 0) { snapshotRepository.findByDay("sim-1", SimulationDay(1)) }
            coVerify(exactly = 0) { simulationEngine.runDay(any(), any(), any()) }
        }
}
