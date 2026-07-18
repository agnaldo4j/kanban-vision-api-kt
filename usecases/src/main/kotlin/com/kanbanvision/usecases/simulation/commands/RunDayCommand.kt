package com.kanbanvision.usecases.simulation.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.usecases.cqs.Command

data class RunDayCommand(
    val simulationId: String,
    val decisions: List<Decision>,
    val callerOrganizationId: String,
) : Command {
    override fun validate(): Either<CommonError.ValidationError, Unit> =
        either {
            ensure(simulationId.isNotBlank()) { CommonError.ValidationError("Simulation id must not be blank") }
            ensure(callerOrganizationId.isNotBlank()) { CommonError.ValidationError("Caller organization id must not be blank") }
        }
}
