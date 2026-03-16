package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.scenario.DailySnapshot
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.ScenarioId

interface SnapshotRepository {
    suspend fun save(snapshot: DailySnapshot): Either<DomainError, DailySnapshot>

    suspend fun findByDay(
        scenarioId: ScenarioId,
        day: SimulationDay,
    ): Either<DomainError, DailySnapshot?>

    suspend fun findAllByScenario(scenarioId: ScenarioId): Either<DomainError, List<DailySnapshot>>
}
