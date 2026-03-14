package com.kanbanvision.usecases.repositories

import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.valueobjects.BoardId

interface BoardRepository {
    suspend fun save(board: Board): Board

    suspend fun findById(id: BoardId): Board?

    suspend fun findAll(): List<Board>

    suspend fun delete(id: BoardId): Boolean
}
