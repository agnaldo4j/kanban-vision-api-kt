package com.kanbanvision.usecases.repositories

import com.kanbanvision.domain.model.Column
import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

interface ColumnRepository {
    suspend fun save(column: Column): Column

    suspend fun findById(id: ColumnId): Column?

    suspend fun findByBoardId(boardId: BoardId): List<Column>

    suspend fun delete(id: ColumnId): Boolean
}
