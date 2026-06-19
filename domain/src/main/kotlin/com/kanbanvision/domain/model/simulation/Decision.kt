package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.kanban.ServiceClass

sealed interface Decision {
    data class MoveItem(
        val cardId: String,
    ) : Decision

    data class BlockItem(
        val cardId: String,
        val reason: String = "blocked",
    ) : Decision

    data class UnblockItem(
        val cardId: String,
    ) : Decision

    data class AddItem(
        val title: String,
        val serviceClass: ServiceClass = ServiceClass.STANDARD,
    ) : Decision
}
