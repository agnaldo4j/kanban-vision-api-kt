package com.kanbanvision.usecases.simulation

import arrow.core.Either
import arrow.core.raise.either
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
            // `timed` mede agora carga + autorização, não só a carga — o guard custa nanossegundos e o
            // helper não devolve Duration de propósito (contaminaria os outros 4 chamadores). Mesma forma
            // aninhada que `ListSimulationsUseCase` já usa: o `either` interno abre um Raise novo e o
            // `bind()` do `timed` re-levanta o Left no Raise externo. No caminho Forbidden o `timed`
            // levanta ANTES do log — igual a antes, sem log.
            val (simulation, duration) =
                timed {
                    either { loadOwnedSimulation(simulationRepository, SimulationId(id), query.callerOrganizationId) }
                }
            log.info("Simulation fetched: id={} duration={}ms", id, duration.inWholeMilliseconds)
            simulation
        }
}
