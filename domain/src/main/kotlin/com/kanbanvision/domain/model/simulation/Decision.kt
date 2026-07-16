package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass

sealed interface Decision {
    data class MoveItem(
        val cardId: CardId,
    ) : Decision

    data class BlockItem(
        val cardId: CardId,
        val reason: String = "blocked",
    ) : Decision

    data class UnblockItem(
        val cardId: CardId,
    ) : Decision

    data class AddItem(
        val title: String,
        val serviceClass: ServiceClass = ServiceClass.STANDARD,
    ) : Decision
}
