package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId

interface SnapshotRepository {
    suspend fun save(snapshot: DailySnapshot): Either<DomainError, DailySnapshot>

    suspend fun findByDay(
        simulationId: SimulationId,
        day: SimulationDay,
    ): Either<DomainError, DailySnapshot?>

    suspend fun findAllBySimulation(simulationId: SimulationId): Either<DomainError, List<DailySnapshot>>
}
