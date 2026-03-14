package com.kanbanvision.usecases.card

import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

class CreateCardUseCase(
    private val cardRepository: CardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateCardCommand): CardId {
        command.validate()
        log.debug("Creating card: columnId={} title={}", command.columnId, command.title)
        val (cardId, duration) =
            measureTimedValue {
                val columnId = ColumnId(command.columnId)
                val existing = cardRepository.findByColumnId(columnId)
                val card =
                    Card.create(
                        columnId = columnId,
                        title = command.title,
                        description = command.description,
                        position = existing.size,
                    )
                cardRepository.save(card)
                card.id
            }
        log.info(
            "Card created: id={} columnId={} title={} duration={}ms",
            cardId.value,
            command.columnId,
            command.title,
            duration.inWholeMilliseconds,
        )
        return cardId
    }
}
