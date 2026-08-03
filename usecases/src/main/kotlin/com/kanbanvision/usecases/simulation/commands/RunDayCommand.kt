package com.kanbanvision.usecases.simulation.commands

import arrow.core.Either
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.usecases.cqs.Command
import com.kanbanvision.usecases.cqs.validateSimulationRef

data class RunDayCommand(
    val simulationId: String,
    val decisions: List<Decision>,
    val callerOrganizationId: String,
) : Command {
    override fun validate(): Either<CommonError.ValidationError, Unit> = validateSimulationRef(simulationId, callerOrganizationId)
}
