package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationResult
import java.time.Instant

interface SimulationEnginePort {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
        now: Instant,
    ): SimulationResult
}
