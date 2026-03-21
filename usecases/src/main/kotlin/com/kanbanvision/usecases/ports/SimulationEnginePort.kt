package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationResult

/**
 * Boundary that executes one simulation day from the use case layer.
 *
 * The domain model stays independent from execution infrastructure,
 * while adapters can provide different engine implementations.
 */
interface SimulationEnginePort {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult
}
