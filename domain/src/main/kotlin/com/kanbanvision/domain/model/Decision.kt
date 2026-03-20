package com.kanbanvision.domain.model

import java.util.UUID

data class Decision(
    val id: String,
    val type: DecisionType,
    val payload: Map<String, String>,
    val audit: Audit = Audit(),
) {
    init {
        require(id.isNotBlank()) { "Decision id must not be blank" }
    }

    companion object {
        fun move(cardId: String): Decision =
            Decision(
                id = UUID.randomUUID().toString(),
                type = DecisionType.MOVE_ITEM,
                payload = mapOf("cardId" to cardId),
            )

        fun block(
            cardId: String,
            reason: String = "blocked",
        ): Decision =
            Decision(
                id = UUID.randomUUID().toString(),
                type = DecisionType.BLOCK_ITEM,
                payload = mapOf("cardId" to cardId, "reason" to reason),
            )

        fun unblock(cardId: String): Decision =
            Decision(
                id = UUID.randomUUID().toString(),
                type = DecisionType.UNBLOCK_ITEM,
                payload = mapOf("cardId" to cardId),
            )

        fun addItem(
            title: String,
            serviceClass: String = "STANDARD",
        ): Decision =
            Decision(
                id = UUID.randomUUID().toString(),
                type = DecisionType.ADD_ITEM,
                payload = mapOf("title" to title, "serviceClass" to serviceClass),
            )
    }
}
