package com.kanbanvision.usecases.simulation

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.common.errors.DomainError
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationId
import com.kanbanvision.usecases.repositories.SimulationRepository

suspend fun Raise<DomainError>.loadOwnedSimulation(
    repository: SimulationRepository,
    id: SimulationId,
    callerOrganizationId: String,
): Simulation {
    val simulation = repository.findById(id).bind()
    ensure(simulation.organization.id == callerOrganizationId) {
        CommonError.Forbidden("Simulation does not belong to the caller's organization")
    }
    return simulation
}
