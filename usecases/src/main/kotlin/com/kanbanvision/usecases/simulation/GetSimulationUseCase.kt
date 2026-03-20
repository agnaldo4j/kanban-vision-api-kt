package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Simulation
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
            val (simulation, duration) = timed { simulationRepository.findById(id) }
            log.info("Simulation fetched: id={} duration={}ms", id, duration.inWholeMilliseconds)
            simulation
        }
}
