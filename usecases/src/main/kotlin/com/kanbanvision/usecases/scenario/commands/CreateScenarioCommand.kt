package com.kanbanvision.usecases.scenario.commands

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Command

private const val MIN_WIP = 1
private const val MIN_TEAM = 1

data class CreateScenarioCommand(
    val tenantId: String,
    val wipLimit: Int,
    val teamSize: Int,
    val seedValue: Long,
) : Command {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either<NonEmptyList<DomainError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(tenantId.isNotBlank()) { DomainError.ValidationError("Tenant id must not be blank") } },
                { ensure(wipLimit >= MIN_WIP) { DomainError.ValidationError("WIP limit must be at least 1") } },
                { ensure(teamSize >= MIN_TEAM) { DomainError.ValidationError("Team size must be at least 1") } },
            ) { _, _, _ -> }
        }.mapLeft { errors -> DomainError.ValidationError(errors.map { it.message }) }
}
