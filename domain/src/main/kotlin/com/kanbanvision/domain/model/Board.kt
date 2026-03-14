package com.kanbanvision.domain.model

import com.kanbanvision.domain.event.BoardCreated
import com.kanbanvision.domain.event.DomainEvent
import com.kanbanvision.domain.model.valueobjects.BoardId
import java.time.Instant

data class Board(
    val id: BoardId,
    val name: String,
    val columns: List<Column> = emptyList(),
    val createdAt: Instant = Instant.now(),
) {
    private val _events: MutableList<DomainEvent> = mutableListOf()
    val events: List<DomainEvent> get() = _events.toList()

    companion object {
        fun create(name: String): Board {
            require(name.isNotBlank()) { "Board name must not be blank" }
            val board = Board(id = BoardId.generate(), name = name)
            board._events.add(BoardCreated(board.id, board.name, board.createdAt))
            return board
        }
    }
}
