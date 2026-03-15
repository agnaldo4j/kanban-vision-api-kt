package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.repositories.CardRepository
import org.slf4j.LoggerFactory
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

class CreateCardUseCase(
    private val cardRepository: CardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(command: CreateCardCommand): Either<DomainError, CardId> =
        either {
            command.validate().bind()
            log.debug("Creating card: columnId={} title={}", command.columnId, command.title)
            val (cardId, duration) = buildAndSave(command).bind()
            log.info("Card created: id={} duration={}ms", cardId.value, duration.inWholeMilliseconds)
            cardId
        }

    private suspend fun buildAndSave(command: CreateCardCommand): Either<DomainError, TimedValue<CardId>> =
        either {
            catch(
                {
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
                },
            ) { e -> raise(DomainError.PersistenceError(e.message ?: "Database error")) }
        }
}
