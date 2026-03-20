package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.SimulationState

interface ScenarioRepository {
    suspend fun save(scenario: Scenario): Either<DomainError, Scenario>

    suspend fun findById(id: String): Either<DomainError, Scenario>

    suspend fun saveState(
        scenarioId: String,
        state: SimulationState,
    ): Either<DomainError, SimulationState>

    suspend fun findState(scenarioId: String): Either<DomainError, SimulationState>
}
