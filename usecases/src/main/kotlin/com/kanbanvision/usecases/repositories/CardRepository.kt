package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId

interface CardRepository {
    suspend fun save(card: Card): Either<DomainError, Card>

    suspend fun findById(id: CardId): Either<DomainError, Card>

    suspend fun findByColumnId(columnId: ColumnId): Either<DomainError, List<Card>>

    suspend fun delete(id: CardId): Either<DomainError, Boolean>
}
