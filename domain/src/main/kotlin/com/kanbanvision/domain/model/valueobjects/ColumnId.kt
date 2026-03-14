package com.kanbanvision.domain.model.valueobjects

import java.util.UUID

@JvmInline
value class ColumnId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ColumnId must not be blank" }
    }

    companion object {
        fun generate(): ColumnId = ColumnId(UUID.randomUUID().toString())
    }
}
