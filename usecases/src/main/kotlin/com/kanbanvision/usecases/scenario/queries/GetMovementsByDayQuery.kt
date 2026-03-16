package com.kanbanvision.usecases.scenario.queries

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

private const val MIN_DAY = 1

data class GetMovementsByDayQuery(
    val scenarioId: String,
    val day: Int,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either<NonEmptyList<DomainError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(scenarioId.isNotBlank()) { DomainError.ValidationError("Scenario id must not be blank") } },
                { ensure(day >= MIN_DAY) { DomainError.ValidationError("Day must be at least 1") } },
            ) { _, _ -> }
        }.mapLeft { errors -> DomainError.ValidationError(errors.map { it.message }) }
}
