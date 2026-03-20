package com.kanbanvision.usecases.repositories

import arrow.core.Either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card

interface CardRepository {
    suspend fun save(card: Card): Either<DomainError, Card>

    suspend fun findById(id: String): Either<DomainError, Card>

    suspend fun updateCard(
        id: String,
        transform: (Card) -> Card,
    ): Either<DomainError, Card>

    suspend fun findByColumnId(columnId: String): Either<DomainError, List<Card>>
}
