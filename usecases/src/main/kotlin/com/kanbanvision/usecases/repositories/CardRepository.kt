package com.kanbanvision.usecases.repositories

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

interface CardRepository {
    suspend fun save(card: Card): Card

    suspend fun findById(id: CardId): Card?

    suspend fun findByColumnId(columnId: ColumnId): List<Card>

    suspend fun delete(id: CardId): Boolean
}
