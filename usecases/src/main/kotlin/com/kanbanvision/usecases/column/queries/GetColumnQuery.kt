package com.kanbanvision.usecases.column.queries

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

data class GetColumnQuery(
    val id: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(id.isNotBlank()) { DomainError.ValidationError("Step id must not be blank") }
        }
}
