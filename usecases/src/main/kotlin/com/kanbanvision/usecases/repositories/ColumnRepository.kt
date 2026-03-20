package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Step

interface ColumnRepository {
    suspend fun save(column: Step): Either<DomainError, Step>

    suspend fun findById(id: String): Either<DomainError, Step>

    suspend fun findByBoardId(boardId: String): Either<DomainError, List<Step>>
}
