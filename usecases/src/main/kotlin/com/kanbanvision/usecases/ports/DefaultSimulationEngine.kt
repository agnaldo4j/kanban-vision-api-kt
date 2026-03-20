package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.domain.model.SimulationState
import com.kanbanvision.domain.simulation.SimulationEngine

/**
 * Default [SimulationEnginePort] implementation that delegates to the pure-domain
 * [SimulationEngine] object. Wired by Koin in production; replaced by a MockK mock in unit tests.
 */
class DefaultSimulationEngine : SimulationEnginePort {
    override fun runDay(
        scenarioId: String,
        state: SimulationState,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult = SimulationEngine.runDay(scenarioId, state, decisions, seed)
}
