package com.kanbanvision.domain.model

import java.util.UUID

data class Decision(
    override val id: String = UUID.randomUUID().toString(),
    val type: DecisionType,
    val payload: Map<String, String>,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Decision id must not be blank" }
    }

    companion object {
        fun move(cardId: String): Decision = Decision(type = DecisionType.MOVE_ITEM, payload = mapOf("cardId" to cardId))

        fun block(
            cardId: String,
            reason: String = "blocked",
        ): Decision = Decision(type = DecisionType.BLOCK_ITEM, payload = mapOf("cardId" to cardId, "reason" to reason))

        fun unblock(cardId: String): Decision = Decision(type = DecisionType.UNBLOCK_ITEM, payload = mapOf("cardId" to cardId))

        fun addItem(
            title: String,
            serviceClass: String = "STANDARD",
        ): Decision = Decision(type = DecisionType.ADD_ITEM, payload = mapOf("title" to title, "serviceClass" to serviceClass))
    }
}
