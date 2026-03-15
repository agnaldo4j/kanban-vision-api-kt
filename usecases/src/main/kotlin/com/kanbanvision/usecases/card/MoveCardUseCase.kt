package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.measureTimedValue

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
            val duration = findAndMove(command).bind()
            log.info(
                "Card moved: cardId={} targetColumnId={} position={} duration={}ms",
                command.cardId,
                command.targetColumnId,
                command.newPosition,
                duration.inWholeMilliseconds,
            )
        }

    private suspend fun findAndMove(command: MoveCardCommand): Either<DomainError, Duration> =
        either {
            val (updateResult, duration) =
                measureTimedValue {
                    cardRepository.updateCard(CardId(command.cardId)) { card ->
                        card.moveTo(ColumnId(command.targetColumnId), command.newPosition)
                    }
                }
            updateResult.bind()
            duration
        }
}
