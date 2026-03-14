package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.valueobjects.BoardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

data class Column(
    val id: ColumnId,
    val boardId: BoardId,
    val name: String,
    val position: Int,
    val cards: List<Card> = emptyList(),
) {
    companion object {
        fun create(boardId: BoardId, name: String, position: Int): Column {
            require(name.isNotBlank()) { "Column name must not be blank" }
            require(position >= 0) { "Column position must be non-negative" }
            return Column(id = ColumnId.generate(), boardId = boardId, name = name, position = position)
        }
    }
}
