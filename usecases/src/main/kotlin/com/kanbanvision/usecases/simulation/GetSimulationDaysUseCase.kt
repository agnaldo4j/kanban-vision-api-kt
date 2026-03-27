package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetSimulationDaysUseCase(
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetSimulationDaysQuery): Either<DomainError, List<DailySnapshot>> =
        either {
            query.validate().bind()
            val id = query.simulationId
            val (snapshots, duration) = timed { snapshotRepository.findAllBySimulation(id) }
            val sorted = snapshots.sortedBy { it.day.value }
            log.info(
                "Simulation days fetched: id={} count={} duration={}ms",
                id,
                sorted.size,
                duration.inWholeMilliseconds,
            )
            sorted
        }
}
