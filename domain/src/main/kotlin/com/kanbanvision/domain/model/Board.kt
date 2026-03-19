package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.team.AbilityName
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

    fun addColumn(
        name: String,
        requiredAbility: AbilityName,
    ): Column {
        require(name.isNotBlank()) { "Column name must not be blank" }
        require(columns.none { it.name == name }) { "Column name '$name' already exists on this board" }
        return Column.create(boardId = id, name = name, position = columns.size, requiredAbility = requiredAbility)
    }

    fun addCard(
        column: Column,
        title: String,
        description: String = "",
    ): Card {
        require(column.boardId == id) { "Column ${column.id.value} does not belong to board ${id.value}" }
        return Card.create(columnId = column.id, title = title, description = description, position = column.cards.size)
    }
}
