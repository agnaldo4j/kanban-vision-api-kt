package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Board

interface BoardRepository {
    suspend fun save(board: Board): Either<DomainError, Board>

    suspend fun findById(id: String): Either<DomainError, Board>
}
