package com.kanbanvision.usecases.scenario

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.movement.Movement
import com.kanbanvision.domain.model.scenario.SimulationDay
import com.kanbanvision.domain.model.valueobjects.ScenarioId
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.scenario.queries.GetMovementsByDayQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetMovementsByDayUseCase(
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetMovementsByDayQuery): Either<DomainError, List<Movement>> =
        either {
            query.validate().bind()
            val id = ScenarioId(query.scenarioId)
            val (snapshot, duration) = timed { snapshotRepository.findByDay(id, SimulationDay(query.day)) }
            ensureNotNull(snapshot) { DomainError.ScenarioNotFound(id.value) }
            log.info(
                "Movements fetched: scenario={} day={} count={} duration={}ms",
                id.value,
                query.day,
                snapshot.movements.size,
                duration.inWholeMilliseconds,
            )
            snapshot.movements
        }
}
