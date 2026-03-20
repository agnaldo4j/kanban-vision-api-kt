package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.usecases.ports.SimulationEnginePort
import com.kanbanvision.usecases.repositories.ScenarioRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.commands.RunDayCommand
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class RunDayUseCase(
    private val scenarioRepository: ScenarioRepository,
    private val snapshotRepository: SnapshotRepository,
    private val simulationEngine: SimulationEnginePort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: RunDayCommand): Either<DomainError, DailySnapshot> =
        either {
            command.validate().bind()
            val id = command.scenarioId
            val scenario = scenarioRepository.findById(id).bind()
            val state = scenarioRepository.findState(id).bind()
            ensureContextConsistency(scenario, state).bind()
            guardDuplicate(id, state).bind()
            val result = simulationEngine.runDay(id, state, command.decisions, scenario.config.seedValue)
            val (snapshot, duration) = timed { persistResult(id, result.newState, result.snapshot) }
            log.info("Day run: scenario={} day={} duration={}ms", id, state.currentDay.value, duration.inWholeMilliseconds)
            snapshot
        }

    private fun ensureContextConsistency(
        scenario: com.kanbanvision.domain.model.Scenario,
        state: SimulationState,
    ): Either<DomainError, Unit> =
        either {
            val context = state.context ?: return@either Unit
            ensure(context.organizationId == scenario.organizationId) {
                DomainError.ValidationError(
                    "Simulation state organizationId (${context.organizationId}) " +
                        "does not match scenario organizationId (${scenario.organizationId})",
                )
            }
            ensure(context.boardId == scenario.boardId) {
                DomainError.ValidationError(
                    "Simulation state boardId (${context.boardId}) does not match scenario boardId (${scenario.boardId})",
                )
            }
            ensureAssignmentsConsistency(context).bind()
        }

    private fun ensureAssignmentsConsistency(context: com.kanbanvision.domain.model.SimulationContext): Either<DomainError, Unit> =
        either {
            context.workerAssignments.forEach { (workerId, stepId) ->
                val worker = context.findWorker(workerId)
                ensure(worker != null) {
                    DomainError.ValidationError("Simulation context contains assignment for unknown workerId: $workerId")
                }
                val step = context.findStep(stepId)
                ensure(step != null) {
                    DomainError.ValidationError("Simulation context contains assignment for unknown stepId: $stepId")
                }
                val resolvedWorker = worker ?: return@forEach
                val resolvedStep = step ?: return@forEach
                ensureStepBoardConsistency(context, resolvedStep, stepId).bind()
                ensureWorkerAbilityConsistency(resolvedWorker, resolvedStep, workerId, stepId).bind()
            }
        }

    private fun ensureStepBoardConsistency(
        context: com.kanbanvision.domain.model.SimulationContext,
        step: com.kanbanvision.domain.model.Step,
        stepId: String,
    ): Either<DomainError, Unit> =
        either {
            ensure(step.boardId == context.boardId) {
                DomainError.ValidationError(
                    "Simulation context contains assignment for stepId $stepId belonging to board ${step.boardId}, " +
                        "which does not match context boardId (${context.boardId})",
                )
            }
        }

    private fun ensureWorkerAbilityConsistency(
        worker: com.kanbanvision.domain.model.Worker,
        step: com.kanbanvision.domain.model.Step,
        workerId: String,
        stepId: String,
    ): Either<DomainError, Unit> =
        either {
            ensure(worker.hasAbility(step.requiredAbility)) {
                DomainError.ValidationError(
                    "Simulation context contains assignment of workerId $workerId to stepId $stepId, " +
                        "but the worker does not have the required ability (${step.requiredAbility})",
                )
            }
        }

    private suspend fun guardDuplicate(
        id: String,
        state: SimulationState,
    ): Either<DomainError, Unit> =
        either {
            val existing = snapshotRepository.findByDay(id, state.currentDay).bind()
            ensure(existing == null) { DomainError.DayAlreadyExecuted(state.currentDay.value) }
        }

    private suspend fun persistResult(
        id: String,
        newState: SimulationState,
        snapshot: DailySnapshot,
    ): Either<DomainError, DailySnapshot> =
        either {
            scenarioRepository.saveState(id, newState).bind()
            snapshotRepository.save(snapshot).bind()
            snapshot
        }
}
