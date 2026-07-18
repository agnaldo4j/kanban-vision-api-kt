package com.kanbanvision.usecases.simulation.commands

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.CommonError
import com.kanbanvision.usecases.cqs.Command

private const val MIN_WIP = 1
private const val MIN_TEAM = 1

data class CreateSimulationCommand(
    val organizationId: String,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
) : Command {
    override fun validate(): Either<CommonError.ValidationError, Unit> =
        either<NonEmptyList<CommonError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(organizationId.isNotBlank()) { CommonError.ValidationError("Organization id must not be blank") } },
                { ensure(wipLimit >= MIN_WIP) { CommonError.ValidationError("WIP limit must be at least 1") } },
                { ensure(teamSize >= MIN_TEAM) { CommonError.ValidationError("Team size must be at least 1") } },
            ) { _, _, _ -> }
        }.mapLeft { errors -> CommonError.ValidationError(errors.map { it.message }) }
}
