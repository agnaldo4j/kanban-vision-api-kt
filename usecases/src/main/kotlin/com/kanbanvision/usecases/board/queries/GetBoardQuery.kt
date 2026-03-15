package com.kanbanvision.usecases.board.queries

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.cqs.Query

data class GetBoardQuery(
    val id: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> =
        if (id.isNotBlank()) {
            Unit.right()
        } else {
            DomainError.ValidationError("Board id must not be blank").left()
        }
}
