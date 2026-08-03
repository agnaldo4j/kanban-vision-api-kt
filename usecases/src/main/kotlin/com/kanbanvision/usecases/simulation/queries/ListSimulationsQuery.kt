package com.kanbanvision.usecases.simulation.queries

import arrow.core.Either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import com.kanbanvision.domain.common.errors.CommonError
import com.kanbanvision.usecases.cqs.Query
import com.kanbanvision.usecases.cqs.accumulateValidation

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
    override fun validate(): Either<CommonError.ValidationError, Unit> =
        accumulateValidation {
            zipOrAccumulate(
                { ensure(organizationId.isNotBlank()) { CommonError.ValidationError("Organization id must not be blank") } },
                { ensure(page >= MIN_PAGE) { CommonError.ValidationError("Page must be at least 1") } },
                { ensure(size in MIN_SIZE..MAX_SIZE) { CommonError.ValidationError("Size must be between 1 and 100") } },
            ) { _, _, _ -> }
        }
}
