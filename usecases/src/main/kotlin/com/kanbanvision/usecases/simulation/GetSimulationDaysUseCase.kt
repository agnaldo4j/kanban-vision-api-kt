package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.repositories.SnapshotRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationDaysQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetSimulationDaysUseCase(
    private val simulationRepository: SimulationRepository,
    private val snapshotRepository: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetSimulationDaysQuery): Either<DomainError, List<DailySnapshot>> =
        either {
            query.validate().bind()
            val id = query.simulationId
            val simulation = simulationRepository.findById(SimulationId(id)).bind()
            ensure(simulation.organization.id == query.callerOrganizationId) {
                CommonError.Forbidden("Simulation does not belong to the caller's organization")
            }
            val (snapshots, duration) = timed { snapshotRepository.findAllBySimulation(SimulationId(id)) }
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
