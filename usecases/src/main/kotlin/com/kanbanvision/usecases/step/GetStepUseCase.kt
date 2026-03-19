package com.kanbanvision.usecases.step

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.column.GetColumnUseCase
import com.kanbanvision.usecases.column.queries.GetColumnQuery
import com.kanbanvision.usecases.step.queries.GetStepQuery

class GetStepUseCase(
    private val getColumnUseCase: GetColumnUseCase,
) {
    suspend fun execute(query: GetStepQuery): Either<DomainError, Step> = getColumnUseCase.execute(GetColumnQuery(query.id))
}
