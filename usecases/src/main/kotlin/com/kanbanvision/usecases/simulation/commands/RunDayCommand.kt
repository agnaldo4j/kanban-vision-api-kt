package com.kanbanvision.usecases.simulation.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Decision
import com.kanbanvision.usecases.cqs.Command

data class RunDayCommand(
    val simulationId: String,
    val decisions: List<Decision>,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(simulationId.isNotBlank()) { DomainError.ValidationError("Simulation id must not be blank") }
        }
}
