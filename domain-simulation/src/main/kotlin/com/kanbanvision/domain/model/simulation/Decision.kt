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

    /**
     * A decision read from a persisted blob that this release cannot interpret — an unrecognised
     * [type], or a payload missing the fields its type requires. Carrying it keeps the surrounding
     * aggregate loadable and lets it round-trip back to storage unchanged; it drives no behaviour.
     */
    data class Unknown(
        val type: String,
        val payload: Map<String, String>,
    ) : Decision
}
