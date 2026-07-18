package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.usecases.cqs.Query

private const val MIN_DAY = 1

data class GetDailySnapshotQuery(
    val simulationId: String,
    val day: Int,
    val callerOrganizationId: String,
) : Query {
    override fun validate(): Either<CommonError.ValidationError, Unit> =
        either<NonEmptyList<CommonError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(simulationId.isNotBlank()) { CommonError.ValidationError("Simulation id must not be blank") } },
                { ensure(day >= MIN_DAY) { CommonError.ValidationError("Day must be at least 1") } },
                { ensure(callerOrganizationId.isNotBlank()) { CommonError.ValidationError("Caller organization id must not be blank") } },
            ) { _, _, _ -> }
        }.mapLeft { errors -> CommonError.ValidationError(errors.map { it.message }) }
}
