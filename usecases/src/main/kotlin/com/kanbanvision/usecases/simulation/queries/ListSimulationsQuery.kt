package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

private const val MIN_PAGE = 1
private const val MIN_SIZE = 1
private const val MAX_SIZE = 100
private const val DEFAULT_PAGE = 1
private const val DEFAULT_SIZE = 20

data class ListSimulationsQuery(
    val organizationId: String,
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either<NonEmptyList<DomainError.ValidationError>, Unit> {
            zipOrAccumulate(
                { ensure(organizationId.isNotBlank()) { DomainError.ValidationError("Organization id must not be blank") } },
                { ensure(page >= MIN_PAGE) { DomainError.ValidationError("Page must be at least 1") } },
                { ensure(size in MIN_SIZE..MAX_SIZE) { DomainError.ValidationError("Size must be between 1 and 100") } },
            ) { _, _, _ -> }
        }.mapLeft { errors -> DomainError.ValidationError(errors.map { it.message }) }
}
