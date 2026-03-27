package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.usecases.Page
import com.kanbanvision.usecases.repositories.SimulationRepository
import com.kanbanvision.usecases.simulation.queries.ListSimulationsQuery
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class ListSimulationsUseCase(
    private val simulationRepository: SimulationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: ListSimulationsQuery): Either<DomainError, Page<Simulation>> =
        either {
            query.validate().bind()
            val orgId = query.organizationId
            val (result, duration) =
                timed {
                    either {
                        val simulations = simulationRepository.findAll(orgId, query.page, query.size).bind()
                        val total = simulationRepository.countByOrganization(orgId).bind()
                        Page(data = simulations, page = query.page, size = query.size, total = total)
                    }
                }
            log.info(
                "Simulations listed: orgId={} page={} size={} duration={}ms",
                orgId,
                query.page,
                query.size,
                duration.inWholeMilliseconds,
            )
            result
        }
}
