package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.usecases.cqs.Query
import com.kanbanvision.usecases.cqs.accumulateValidation
import com.kanbanvision.usecases.cqs.ensureCallerOrganizationId
import com.kanbanvision.usecases.cqs.ensureSimulationId

private const val MIN_DAY = 1

data class GetDailySnapshotQuery(
    val simulationId: String,
    val day: Int,
    val callerOrganizationId: String,
) : Query {
    override fun validate(): Either<CommonError.ValidationError, Unit> =
        accumulateValidation {
            zipOrAccumulate(
                { ensureSimulationId(simulationId) },
                { ensure(day >= MIN_DAY) { CommonError.ValidationError("Day must be at least 1") } },
                { ensureCallerOrganizationId(callerOrganizationId) },
            ) { _, _, _ -> }
        }
}
