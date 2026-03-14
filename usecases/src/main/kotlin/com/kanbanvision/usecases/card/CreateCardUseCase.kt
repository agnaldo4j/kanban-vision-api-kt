package com.kanbanvision.usecases.card

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.domain.port.CardRepository

class CreateCardUseCase(
    private val cardRepository: CardRepository,
) {
    suspend fun execute(columnId: ColumnId, title: String, description: String = ""): Card {
        val existing = cardRepository.findByColumnId(columnId)
        val card = Card.create(
            columnId = columnId,
            title = title,
            description = description,
            position = existing.size,
        )
        return cardRepository.save(card)
    }
}
