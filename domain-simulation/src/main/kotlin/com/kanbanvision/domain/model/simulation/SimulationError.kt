package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.common.errors.DomainError

/**
 * Erros do Simulation BC (`:domain-simulation` — GAP-CL/ADR-0038), co-localizados com o agregado.
 */
sealed interface SimulationError : DomainError {
    data class SimulationNotFound(
        val id: String,
    ) : SimulationError

    data class InvalidDecision(
        val reason: String,
    ) : SimulationError

    data class DayAlreadyExecuted(
        val day: Int,
    ) : SimulationError {
        init {
            require(day >= 1) { "DayAlreadyExecuted day must be at least 1" }
        }
    }
}
