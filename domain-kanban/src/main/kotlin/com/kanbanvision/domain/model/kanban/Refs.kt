package com.kanbanvision.domain.model.kanban

@JvmInline
value class BoardId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "BoardId must not be blank" }
    }
}

@JvmInline
value class StepId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "StepId must not be blank" }
    }
}

@JvmInline
value class CardId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CardId must not be blank" }
    }
}
