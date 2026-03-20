package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.model.Decision
import com.kanbanvision.domain.model.SimulationResult
import com.kanbanvision.domain.model.SimulationState

/**
 * Port for the simulation engine. Defined in `usecases/` so that [RunDayUseCase] depends on an
 * abstraction rather than the concrete [com.kanbanvision.domain.simulation.SimulationEngine] object,
 * enabling MockK-based unit tests and future span injection without touching domain code.
 */
interface SimulationEnginePort {
    fun runDay(
        scenarioId: String,
        state: SimulationState,
        decisions: List<Decision>,
        seed: Long,
    ): SimulationResult
}
