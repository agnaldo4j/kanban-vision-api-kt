package com.kanbanvision.usecases.card

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.domain.port.CardRepository

class MoveCardUseCase(
    private val cardRepository: CardRepository,
) {
    suspend fun execute(cardId: CardId, targetColumnId: ColumnId, newPosition: Int): Card {
        val card = cardRepository.findById(cardId)
            ?: throw NoSuchElementException("Card '${cardId.value}' not found")
        val moved = card.moveTo(targetColumnId, newPosition)
        return cardRepository.save(moved)
    }
}
