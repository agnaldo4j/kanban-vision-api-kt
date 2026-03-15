package com.kanbanvision.usecases.card

import arrow.core.Either
import arrow.core.raise.either
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.Card
import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.usecases.card.queries.GetCardQuery
import com.kanbanvision.usecases.repositories.CardRepository
import org.slf4j.LoggerFactory
import kotlin.time.measureTimedValue

class GetCardUseCase(
    private val cardRepository: CardRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun execute(query: GetCardQuery): Either<DomainError, Card> =
        either {
            query.validate().bind()
            log.debug("Fetching card: id={}", query.id)
            val (result, duration) = measureTimedValue { cardRepository.findById(CardId(query.id)) }
            val card = result.bind()
            log.info("Card fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
            card
        }
}
