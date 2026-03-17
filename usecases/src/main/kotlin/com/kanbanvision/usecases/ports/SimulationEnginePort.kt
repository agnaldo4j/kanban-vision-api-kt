package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.decision.Decision
import com.kanbanvision.domain.model.scenario.SimulationResult
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId

/**
 * Port for the simulation engine. Defined in `usecases/` so that [RunDayUseCase] depends on an
 * abstraction rather than the concrete [com.kanbanvision.domain.simulation.SimulationEngine] object,
 * enabling MockK-based unit tests and future span injection without touching domain code.
 */
interface SimulationEnginePort {
    fun runDay(
        scenarioId: ScenarioId,
        state: SimulationState,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult
}
