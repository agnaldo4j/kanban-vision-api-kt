package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId

interface BoardRepository {
    suspend fun save(board: Board): Either<DomainError, Board>

    suspend fun findById(id: BoardId): Either<DomainError, Board>
}
