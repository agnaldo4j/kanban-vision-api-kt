package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetDailySnapshotUseCase(
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetDailySnapshotQuery): Either<DomainError, DailySnapshot> =
        either {
            query.validate().bind()
            val id = query.scenarioId
            val (snapshot, duration) = timed { snapshotRepository.findByDay(id, SimulationDay(query.day)) }
            ensureNotNull(snapshot) { DomainError.ScenarioNotFound(id) }
            log.info("Snapshot fetched: scenario={} day={} duration={}ms", id, query.day, duration.inWholeMilliseconds)
            snapshot
        }
}
