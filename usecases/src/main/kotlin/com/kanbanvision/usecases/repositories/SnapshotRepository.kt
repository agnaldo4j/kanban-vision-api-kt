package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.SimulationDay

interface SnapshotRepository {
    suspend fun save(snapshot: DailySnapshot): Either<DomainError, DailySnapshot>

    suspend fun findByDay(
        scenarioId: String,
        day: SimulationDay,
    ): Either<DomainError, DailySnapshot?>

    suspend fun findAllByScenario(scenarioId: String): Either<DomainError, List<DailySnapshot>>
}
