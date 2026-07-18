package com.kanbanvision.domain.errors

/**
 * Erros do Simulation BC (futuro `domain-simulation` — ADR-0038).
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
