package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

data class GetSimulationCfdQuery(
    val simulationId: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(simulationId.isNotBlank()) { DomainError.ValidationError("Simulation id must not be blank") }
        }
}
