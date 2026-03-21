package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.domain.simulation.SimulationEngine

class DefaultSimulationEngine : SimulationEnginePort {
    override fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult = SimulationEngine.runDay(simulation, decisions, seed)
}
