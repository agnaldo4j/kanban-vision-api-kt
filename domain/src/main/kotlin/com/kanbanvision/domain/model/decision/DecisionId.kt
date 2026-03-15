package com.kanbanvision.domain.model.decision

import java.util.UUID

@JvmInline
value class DecisionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "DecisionId must not be blank" }
    }

    companion object {
        fun generate(): DecisionId = DecisionId(UUID.randomUUID().toString())
    }
}
