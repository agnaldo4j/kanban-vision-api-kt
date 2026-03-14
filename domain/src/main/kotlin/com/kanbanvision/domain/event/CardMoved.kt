package com.kanbanvision.domain.event

import com.kanbanvision.domain.model.valueobjects.CardId
import com.kanbanvision.domain.model.valueobjects.ColumnId
import java.time.Instant

data class CardMoved(
    val cardId: CardId,
    val fromColumnId: ColumnId,
    val toColumnId: ColumnId,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
