package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
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
                "Moving card: cardId={} targetStepId={} position={}",
                command.cardId,
                command.targetStepId,
                command.newPosition,
            )
            val (_, duration) =
                timed {
                    cardRepository.updateCard(command.cardId) { card ->
                        card.moveTo(command.targetStepId, command.newPosition)
                    }
                }
            log.info(
                "Card moved: cardId={} targetStepId={} position={} duration={}ms",
                command.cardId,
                command.targetStepId,
                command.newPosition,
                duration.inWholeMilliseconds,
            )
        }
}
