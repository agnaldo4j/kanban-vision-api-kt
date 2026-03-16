package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.simulation.SimulationEngine
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.commands.RunDayCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class RunDayUseCase(
    private val scenarioRepository: ScenarioRepository,
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot> =
        either {
            command.validate().bind()
            val id = ScenarioId(command.scenarioId)
            val scenario = scenarioRepository.findById(id).bind()
            val state = scenarioRepository.findState(id).bind()
            guardDuplicate(id, state).bind()
            val result = SimulationEngine.runDay(id, state, command.decisions, scenario.config.seedValue)
            val (snapshot, duration) = timed { persistResult(id, result.newState, result.snapshot) }
            log.info("Day run: scenario={} day={} duration={}ms", id.value, state.currentDay.value, duration.inWholeMilliseconds)
            snapshot
        }

    private suspend fun guardDuplicate(
        id: ScenarioId,
        state: SimulationState,
    ): Either<DomainError, Unit> =
        either {
            val existing = snapshotRepository.findByDay(id, state.currentDay).bind()
            ensure(existing == null) { DomainError.DayAlreadyExecuted(state.currentDay.value) }
        }

    private suspend fun persistResult(
        id: ScenarioId,
        newState: SimulationState,
        snapshot: DailySnapshot,
    ): Either<DomainError, DailySnapshot> =
        either {
            scenarioRepository.saveState(id, newState).bind()
            snapshotRepository.save(snapshot).bind()
            snapshot
        }
}
