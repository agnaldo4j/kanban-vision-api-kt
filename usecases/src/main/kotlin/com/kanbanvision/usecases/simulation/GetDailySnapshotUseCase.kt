package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationError
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetDailySnapshotQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetDailySnapshotUseCase(
    private val simulationRepository: SimulationRepository,
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetDailySnapshotQuery): Either<DomainError, DailySnapshot> =
        either {
            query.validate().bind()
            val id = query.simulationId
            val simulation = simulationRepository.findById(SimulationId(id)).bind()
            ensure(simulation.organization.id == query.callerOrganizationId) {
                CommonError.Forbidden("Simulation does not belong to the caller's organization")
            }
            val (snapshot, duration) = timed { snapshotRepository.findByDay(SimulationId(id), SimulationDay(query.day)) }
            ensureNotNull(snapshot) { SimulationError.SnapshotNotFound(id, query.day) }
            log.info("Snapshot fetched: simulation={} day={} duration={}ms", id, query.day, duration.inWholeMilliseconds)
            snapshot
        }
}
