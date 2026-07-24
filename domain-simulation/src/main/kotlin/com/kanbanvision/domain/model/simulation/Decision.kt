package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.CardId
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
        val title: NonBlankTitle,
        val serviceClass: ServiceClass = ServiceClass.STANDARD,
    ) : Decision
}
