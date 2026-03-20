package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationResult

interface SimulationEnginePort {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult
}
