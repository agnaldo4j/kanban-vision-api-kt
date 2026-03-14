package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.valueobjects.BoardId
import java.time.Instant

data class Board(
    val id: BoardId,
    val name: String,
    val columns: List<Column> = emptyList(),
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        fun create(name: String): Board {
            require(name.isNotBlank()) { "Board name must not be blank" }
            return Board(id = BoardId.generate(), name = name)
        }
    }
}
