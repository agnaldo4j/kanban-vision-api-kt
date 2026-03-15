package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import com.kanbanvision.usecases.timed
import org.slf4j.LoggerFactory

class CreateCardUseCase(
    private val cardRepository: CardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateCardCommand): Either<DomainError, CardId> =
        either {
            command.validate().bind()
            log.debug("Creating card: columnId={} title={}", command.columnId, command.title)
            val (cardId, duration) =
                timed {
                    either {
                        val columnId = ColumnId(command.columnId)
                        val existing = cardRepository.findByColumnId(columnId).bind()
                        val card =
                            Card.create(
                                columnId = columnId,
                                title = command.title,
                                description = command.description,
                                position = existing.size,
                            )
                        cardRepository.save(card).bind()
                        card.id
                    }
                }
            log.info("Card created: id={} duration={}ms", cardId.value, duration.inWholeMilliseconds)
            cardId
        }
}
