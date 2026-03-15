package com.kanbanvision.domain.model.decision

data class Decision(
    val id: DecisionId,
    val type: DecisionType,
    val payload: Map<String, String>,
) {
    companion object {
        fun move(workItemId: String): Decision =
            Decision(
                id = DecisionId.generate(),
                type = DecisionType.MOVE_ITEM,
                payload = mapOf("workItemId" to workItemId),
            )

        fun block(
            workItemId: String,
            reason: String = "blocked",
        ): Decision =
            Decision(
                id = DecisionId.generate(),
                type = DecisionType.BLOCK_ITEM,
                payload = mapOf("workItemId" to workItemId, "reason" to reason),
            )

        fun unblock(workItemId: String): Decision =
            Decision(
                id = DecisionId.generate(),
                type = DecisionType.UNBLOCK_ITEM,
                payload = mapOf("workItemId" to workItemId),
            )

        fun addItem(
            title: String,
            serviceClass: String = "STANDARD",
        ): Decision =
            Decision(
                id = DecisionId.generate(),
                type = DecisionType.ADD_ITEM,
                payload = mapOf("title" to title, "serviceClass" to serviceClass),
            )
    }
}
