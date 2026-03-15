package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class MoveCardUseCase(
    private val cardRepository: CardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: MoveCardCommand): Either<DomainError, Unit> =
        either {
            command.validate().bind()
            log.debug(
                "Moving card: cardId={} targetColumnId={} position={}",
                command.cardId,
                command.targetColumnId,
                command.newPosition,
            )
            val (_, duration) =
                timed {
                    cardRepository.updateCard(CardId(command.cardId)) { card ->
                        card.moveTo(ColumnId(command.targetColumnId), command.newPosition)
                    }
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
