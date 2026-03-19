package com.kanbanvision.usecases.step.queries

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
import com.kanbanvision.usecases.cqs.Query

data class ListStepsByBoardQuery(
    val boardId: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> = ListColumnsByBoardQuery(boardId).validate()
}
