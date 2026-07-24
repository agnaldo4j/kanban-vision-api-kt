package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationResult
import com.kanbanvision.domain.simulation.SimulationEngine
import java.time.Instant

class DefaultSimulationEngine : SimulationEnginePort {
    override fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
        now: Instant,
    ): SimulationResult = SimulationEngine.runDay(simulation, decisions, seed, now)
}
