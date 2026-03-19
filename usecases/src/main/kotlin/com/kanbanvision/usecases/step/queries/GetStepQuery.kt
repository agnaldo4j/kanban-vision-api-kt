package com.kanbanvision.usecases.step.queries

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.cqs.Query

data class GetStepQuery(
    val id: String,
) : Query {
    override fun validate(): Either<DomainError.ValidationError, Unit> = GetColumnQuery(id).validate()
}
