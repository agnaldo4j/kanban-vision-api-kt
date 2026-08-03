package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.usecases.cqs.Query
import com.kanbanvision.usecases.cqs.validateSimulationRef

data class GetSimulationCfdQuery(
    val simulationId: String,
    val callerOrganizationId: String,
) : Query {
    override fun validate(): Either<CommonError.ValidationError, Unit> = validateSimulationRef(simulationId, callerOrganizationId)
}
