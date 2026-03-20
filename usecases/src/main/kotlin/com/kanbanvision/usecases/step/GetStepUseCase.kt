package com.kanbanvision.usecases.step

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.step.queries.GetStepQuery

class GetStepUseCase(
    private val getColumnUseCase: GetColumnUseCase,
) {
    suspend fun execute(query: GetStepQuery): Either<DomainError, Step> =
        either {
            query.validate().bind()
            getColumnUseCase.execute(GetColumnQuery(query.id)).bind()
        }
}
