package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot> =
        either {
            command.validate().bind()
            val simulation = simulationRepository.findById(command.simulationId).bind()
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
        simulationId: String,
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
            snapshot
        }
}
