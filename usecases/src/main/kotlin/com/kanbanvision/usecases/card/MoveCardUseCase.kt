package com.kanbanvision.usecases.card

import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class MoveCardUseCase(
    private val cardRepository: CardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: MoveCardCommand) {
        command.validate()
        log.debug(
            "Moving card: cardId={} targetColumnId={} position={}",
            command.cardId,
            command.targetColumnId,
            command.newPosition,
        )
        val duration =
            measureTime {
                val card =
                    cardRepository.findById(CardId(command.cardId))
                        ?: throw NoSuchElementException("Card '${command.cardId}' not found")
                val moved = card.moveTo(ColumnId(command.targetColumnId), command.newPosition)
                cardRepository.save(moved)
            }
        log.info(
            "Card moved: cardId={} targetColumnId={} position={} duration={}ms",
            command.cardId,
            command.targetColumnId,
            command.newPosition,
            duration.inWholeMilliseconds,
        )
    }
}
