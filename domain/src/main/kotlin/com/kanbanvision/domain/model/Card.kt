package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import java.time.Instant

data class Card(
    val id: CardId,
    val columnId: ColumnId,
    val title: String,
    val description: String = "",
    val position: Int,
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        fun create(
            columnId: ColumnId,
            title: String,
            description: String = "",
            position: Int,
        ): Card {
            require(title.isNotBlank()) { "Card title must not be blank" }
            return Card(id = CardId.generate(), columnId = columnId, title = title, description = description, position = position)
        }
    }

    fun moveTo(
        targetColumnId: ColumnId,
        newPosition: Int,
    ): Card = copy(columnId = targetColumnId, position = newPosition)
}
