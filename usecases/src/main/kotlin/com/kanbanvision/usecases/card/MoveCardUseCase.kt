package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.catch
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
            val (maybeCard, findDuration) =
                catch(
                    { measureTimedValue { cardRepository.findById(CardId(command.cardId)) } },
                ) { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
            val card = maybeCard ?: raise(DomainError.CardNotFound(command.cardId))
            val (_, saveDuration) =
                catch(
                    {
                        measureTimedValue {
                            val moved = card.moveTo(ColumnId(command.targetColumnId), command.newPosition)
                            cardRepository.save(moved)
                        }
                    },
                ) { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
            findDuration + saveDuration
        }
}
