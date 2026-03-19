package com.kanbanvision.usecases.step

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.usecases.board.GetBoardUseCase
import com.kanbanvision.usecases.board.queries.GetBoardQuery
import com.kanbanvision.usecases.column.ListColumnsByBoardUseCase
import com.kanbanvision.usecases.column.queries.ListColumnsByBoardQuery
import com.kanbanvision.usecases.step.queries.ListStepsByBoardQuery

class ListStepsByBoardUseCase(
    private val getBoardUseCase: GetBoardUseCase,
    private val listColumnsByBoardUseCase: ListColumnsByBoardUseCase,
) {
    suspend fun execute(query: ListStepsByBoardQuery): Either<DomainError, List<Step>> =
        either {
            getBoardUseCase.execute(GetBoardQuery(query.boardId)).bind()
            listColumnsByBoardUseCase.execute(ListColumnsByBoardQuery(query.boardId)).bind()
        }
}
