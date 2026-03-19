package com.kanbanvision.usecases.step

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
import com.kanbanvision.usecases.step.queries.ListStepsByBoardQuery

class ListStepsByBoardUseCase(
    private val listColumnsByBoardUseCase: ListColumnsByBoardUseCase,
) {
    suspend fun execute(query: ListStepsByBoardQuery): Either<DomainError, List<Step>> =
        listColumnsByBoardUseCase.execute(ListColumnsByBoardQuery(query.boardId))
}
