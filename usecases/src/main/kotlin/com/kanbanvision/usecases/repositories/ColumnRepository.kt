package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

interface ColumnRepository {
    suspend fun save(column: Column): Either<DomainError, Column>

    suspend fun findById(id: ColumnId): Either<DomainError, Column>

    suspend fun findByBoardId(boardId: BoardId): Either<DomainError, List<Column>>

    suspend fun delete(id: ColumnId): Either<DomainError, Boolean>
}
