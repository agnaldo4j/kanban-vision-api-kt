package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

interface StepRepository {
    suspend fun save(step: Step): Either<DomainError, Step>

    suspend fun findById(id: ColumnId): Either<DomainError, Step>

    suspend fun findByBoardId(boardId: BoardId): Either<DomainError, List<Step>>
}
