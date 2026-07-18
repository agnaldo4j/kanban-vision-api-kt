package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.usecases.cqs.Query

data class GetSimulationQuery(
    val simulationId: String,
    val callerOrganizationId: String,
) : Query {
    override fun validate(): Either<CommonError.ValidationError, Unit> =
        either {
            ensure(simulationId.isNotBlank()) { CommonError.ValidationError("Simulation id must not be blank") }
            ensure(callerOrganizationId.isNotBlank()) { CommonError.ValidationError("Caller organization id must not be blank") }
        }
}
