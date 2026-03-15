package com.kanbanvision.domain.model.valueobjects

import java.util.UUID

@JvmInline
value class WorkItemId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "WorkItemId must not be blank" }
    }

    companion object {
        fun generate(): WorkItemId = WorkItemId(UUID.randomUUID().toString())
    }
}
