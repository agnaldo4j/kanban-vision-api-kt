package com.kanbanvision.usecases.card.queries

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

data class GetCardQuery(
    val id: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        either {
            ensure(id.isNotBlank()) { DomainError.ValidationError("Card id must not be blank") }
        }
}
