package com.kanbanvision.usecases.card

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

    suspend fun execute(query: GetCardQuery): Card {
        query.validate()
        log.debug("Fetching card: id={}", query.id)
        val (card, duration) =
            measureTimedValue {
                cardRepository.findById(CardId(query.id))
                    ?: throw NoSuchElementException("Card '${query.id}' not found")
            }
        log.info("Card fetched: id={} duration={}ms", query.id, duration.inWholeMilliseconds)
        return card
    }
}
