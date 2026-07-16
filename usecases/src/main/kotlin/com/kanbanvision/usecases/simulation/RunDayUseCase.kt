package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.events.DomainEvent
import com.kanbanvision.domain.model.SimulationId
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.usecases.ports.EventPublisherPort
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.commands.RunDayCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class RunDayUseCase(
    private val simulationRepository: SimulationRepository,
    private val snapshotRepository: SnapshotRepository,
    private val simulationEngine: SimulationEnginePort,
    private val publisher: EventPublisherPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot> =
        either {
            command.validate().bind()
            val simulation = simulationRepository.findById(SimulationId(command.simulationId)).bind()
            ensure(simulation.organization.id == command.callerOrganizationId) {
                DomainError.Forbidden("Simulation does not belong to the caller's organization")
            }
            guardDuplicate(simulation.id, simulation.currentDay.value).bind()

            val result = simulationEngine.runDay(simulation, command.decisions, simulation.scenario.rules.seedValue)
            val (snapshot, duration) = timed { persistResult(result.simulation, result.snapshot) }
            log.info(
                "Day run: simulation={} day={} duration={}ms",
                simulation.id,
                simulation.currentDay.value,
                duration.inWholeMilliseconds,
            )
            snapshot
        }

    private suspend fun guardDuplicate(
        simulationId: SimulationId,
        currentDay: Int,
    ): Either<DomainError, Unit> =
        either {
            val existing =
                snapshotRepository
                    .findByDay(simulationId, SimulationDay(currentDay))
                    .bind()
            ensure(existing == null) { DomainError.DayAlreadyExecuted(currentDay) }
        }

    private suspend fun persistResult(
        updatedSimulation: Simulation,
        snapshot: DailySnapshot,
    ): Either<DomainError, DailySnapshot> =
        either {
            simulationRepository.save(updatedSimulation).bind()
            snapshotRepository.save(snapshot).bind()
            publisher.publish(buildDomainEvents(updatedSimulation.id.value, snapshot))
            snapshot
        }

    private fun buildDomainEvents(
        simulationId: String,
        snapshot: DailySnapshot,
    ): List<DomainEvent> =
        buildList {
            snapshot.movements.forEach { movement ->
                when (movement.type) {
                    MovementType.COMPLETED ->
                        add(DomainEvent.CardCompleted(simulationId, movement.cardId.value, snapshot.day.value))
                    MovementType.BLOCKED ->
                        add(DomainEvent.CardBlocked(simulationId, movement.cardId.value, snapshot.day.value, movement.reason))
                    MovementType.MOVED ->
                        add(DomainEvent.CardMoved(simulationId, movement.cardId.value, snapshot.day.value))
                    MovementType.UNBLOCKED ->
                        add(DomainEvent.CardUnblocked(simulationId, movement.cardId.value, snapshot.day.value))
                }
            }
            add(
                DomainEvent.SimulationDayExecuted(
                    simulationId = simulationId,
                    day = snapshot.day.value,
                    throughput = snapshot.metrics.throughput,
                    wipCount = snapshot.metrics.wipCount,
                    blockedCount = snapshot.metrics.blockedCount,
                ),
            )
        }
}
