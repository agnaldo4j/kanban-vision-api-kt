package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.BoardRepository
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.repositories.StepRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateCardUseCase(
    private val cardRepository: CardRepository,
    private val stepRepository: StepRepository,
    private val boardRepository: BoardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateCardCommand): Either<DomainError, String> =
        either {
            command.validate().bind()
            log.debug("Creating card: stepId={} title={}", command.stepId, command.title)
            val (cardId, duration) =
                timed {
                    either {
                        val stepId = command.stepId
                        val step = stepRepository.findById(stepId).bind()
                        val board = boardRepository.findById(step.boardId).bind()
                        val existingCards = cardRepository.findByStepId(stepId).bind()
                        val hydratedStep = step.copy(cards = existingCards)
                        val card =
                            try {
                                board.addCard(hydratedStep, command.title, command.description)
                            } catch (e: IllegalArgumentException) {
                                raise(DomainError.ValidationError(e.message ?: "Invalid card"))
                            }
                        cardRepository.save(card).bind()
                        card.id
                    }
                }
            log.info("Card created: id={} duration={}ms", cardId, duration.inWholeMilliseconds)
            cardId
        }
}
