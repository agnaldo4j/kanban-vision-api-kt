package com.kanbanvision.usecases.simulation

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationError
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.domain.model.simulation.SimulationResult
import com.kanbanvision.domain.simulation.events.DomainEvent
import com.kanbanvision.usecases.ports.EventPublisherPort
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunDayUseCaseTest {
    private val simulationRepository = mockk<SimulationRepository>()
    private val snapshotRepository = mockk<SnapshotRepository>()
    private val simulationEngine = mockk<SimulationEnginePort>()
    private val publisher = mockk<EventPublisherPort>(relaxed = true)
    private val useCase = RunDayUseCase(simulationRepository, snapshotRepository, simulationEngine, publisher)

    @Test
    fun `given already executed day when running simulation day then conflict error is returned`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", day = 3)
            val existingSnapshot = fixtureSnapshot(simulationId = "sim-1", day = 3)

            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(3)) } returns existingSnapshot.right()

            val result = useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList(), callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            val error = result.leftOrNull()
            assertIs<SimulationError.DayAlreadyExecuted>(error)
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

            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1)) } returns null.right()
            coEvery {
                simulationEngine.runDay(simulation = simulation, decisions = any(), seed = simulation.scenario.rules.seedValue)
            } returns SimulationResult(simulation = updatedSimulation, snapshot = snapshot)
            coEvery { simulationRepository.save(updatedSimulation) } returns updatedSimulation.right()
            coEvery { snapshotRepository.save(snapshot) } returns snapshot.right()

            val decision = Decision.MoveItem(CardId("card-1"))
            val command = RunDayCommand(simulationId = "sim-1", decisions = listOf(decision), callerOrganizationId = "org-1")
            val result = useCase.execute(command)

            assertTrue(result.isRight())
            assertEquals(snapshot.id, result.getOrNull()?.id)

            coVerify(exactly = 1) { simulationRepository.findById(SimulationId("sim-1")) }
            coVerify(exactly = 1) { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1)) }
            coVerify(exactly = 1) { simulationEngine.runDay(simulation, any(), simulation.scenario.rules.seedValue) }
            coVerify(exactly = 1) { simulationRepository.save(updatedSimulation) }
            coVerify(exactly = 1) { snapshotRepository.save(snapshot) }
            verify(exactly = 1) {
                publisher.publish(
                    match { events ->
                        events.any { it is DomainEvent.SimulationDayExecuted }
                    },
                )
            }
        }

    @Test
    fun `given snapshot with all movement types when running day then each produces its typed event`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", day = 1)
            val updatedSimulation = fixtureSimulation(id = "sim-1", day = 2)
            val snapshot = fixtureSnapshotWithAllMovements(simulationId = "sim-1", day = 1)

            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()
            coEvery { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1)) } returns null.right()
            coEvery { simulationEngine.runDay(simulation, any(), any()) } returns
                SimulationResult(simulation = updatedSimulation, snapshot = snapshot)
            coEvery { simulationRepository.save(updatedSimulation) } returns updatedSimulation.right()
            coEvery { snapshotRepository.save(snapshot) } returns snapshot.right()

            useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList(), callerOrganizationId = "org-1"))

            verify(exactly = 1) {
                publisher.publish(
                    match { events ->
                        events.any { it is DomainEvent.CardMoved } &&
                            events.any { it is DomainEvent.CardBlocked } &&
                            events.any { it is DomainEvent.CardUnblocked } &&
                            events.any { it is DomainEvent.CardCompleted }
                    },
                )
            }
        }

    @Test
    fun `given blank simulation id when running simulation day then validation error is returned`() =
        runTest {
            val result = useCase.execute(RunDayCommand(simulationId = "", decisions = emptyList(), callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<CommonError.ValidationError>(result.leftOrNull())

            coVerify(exactly = 0) { simulationRepository.findById(any()) }
        }

    @Test
    fun `given simulation of another organization when running simulation day then forbidden without side effects`() =
        runTest {
            val simulation = fixtureSimulation(id = "sim-1", day = 1, organizationId = "org-owner")
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns simulation.right()

            val result =
                useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList(), callerOrganizationId = "org-attacker"))

            assertTrue(result.isLeft())
            assertIs<CommonError.Forbidden>(result.leftOrNull())

            // wasNot Called evita any() no value class SimulationDay (pitfall MockK, testing.md).
            coVerify { snapshotRepository wasNot Called }
            coVerify { simulationEngine wasNot Called }
            coVerify(exactly = 0) { simulationRepository.save(any()) }
        }

    @Test
    fun `given repository failure when loading simulation then error is propagated without side effects`() =
        runTest {
            coEvery { simulationRepository.findById(SimulationId("sim-1")) } returns CommonError.PersistenceError("db unavailable").left()

            val result = useCase.execute(RunDayCommand(simulationId = "sim-1", decisions = emptyList(), callerOrganizationId = "org-1"))

            assertTrue(result.isLeft())
            assertIs<CommonError.PersistenceError>(result.leftOrNull())

            coVerify(exactly = 0) { snapshotRepository.findByDay(SimulationId("sim-1"), SimulationDay(1)) }
            coVerify(exactly = 0) { simulationEngine.runDay(any(), any(), any()) }
        }
}
