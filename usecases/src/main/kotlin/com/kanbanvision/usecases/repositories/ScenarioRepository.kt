package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.Scenario
import com.kanbanvision.domain.model.scenario.SimulationState
import com.kanbanvision.domain.model.valueobjects.ScenarioId

interface ScenarioRepository {
    suspend fun save(scenario: Scenario): Either<DomainError, Scenario>

    suspend fun findById(id: ScenarioId): Either<DomainError, Scenario>

    suspend fun saveState(
        scenarioId: ScenarioId,
        state: SimulationState,
    ): Either<DomainError, SimulationState>

    suspend fun findState(scenarioId: ScenarioId): Either<DomainError, SimulationState>
}
