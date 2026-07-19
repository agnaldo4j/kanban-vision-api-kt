package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.queries.GetSimulationQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class GetSimulationUseCase(
    private val simulationRepository: SimulationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetSimulationQuery): Either<DomainError, Simulation> =
        either {
            query.validate().bind()
            val id = query.simulationId
            val (simulation, duration) = timed { simulationRepository.findById(SimulationId(id)) }
            ensure(simulation.organization.id == query.callerOrganizationId) {
                CommonError.Forbidden("Simulation does not belong to the caller's organization")
            }
            log.info("Simulation fetched: id={} duration={}ms", id, duration.inWholeMilliseconds)
            simulation
        }
}
