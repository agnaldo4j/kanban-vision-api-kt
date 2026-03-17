package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.decision.Decision
import com.kanbanvision.domain.model.scenario.SimulationResult
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.domain.simulation.SimulationEngine

/**
 * Default [SimulationEnginePort] implementation that delegates to the pure-domain
 * [SimulationEngine] object. Wired by Koin in production; replaced by a MockK mock in unit tests.
 */
class DefaultSimulationEngine : SimulationEnginePort {
    override fun runDay(
        scenarioId: ScenarioId,
        state: SimulationState,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult = SimulationEngine.runDay(scenarioId, state, decisions, seed)
}
