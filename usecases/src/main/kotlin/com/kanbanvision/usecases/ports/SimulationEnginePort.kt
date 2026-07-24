package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationResult
import java.time.Instant

/**
 * Boundary that executes one simulation day from the use case layer.
 *
 * The domain model stays independent from execution infrastructure,
 * while adapters can provide different engine implementations.
 *
 * Both `seed` and `now` are sourced at the edge (use case) and passed in, so the
 * engine stays a pure, referentially transparent function of its inputs (GAP-DK).
 */
interface SimulationEnginePort {
    fun runDay(
        simulation: Simulation,
        decisions: List<Decision>,
        seed: Long,
        now: Instant,
    ): SimulationResult
}
