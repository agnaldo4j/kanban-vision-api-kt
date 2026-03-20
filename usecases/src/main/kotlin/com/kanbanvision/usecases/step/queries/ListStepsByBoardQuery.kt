package com.kanbanvision.usecases.step.queries

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

data class ListStepsByBoardQuery(
    val boardId: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(boardId.isNotBlank()) { DomainError.ValidationError("Board id must not be blank") }
        }
}
