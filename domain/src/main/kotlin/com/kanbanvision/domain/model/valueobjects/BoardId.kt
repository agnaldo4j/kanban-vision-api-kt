package com.kanbanvision.domain.model.valueobjects

import java.util.UUID

@JvmInline
value class BoardId(val value: String) {
    init { require(value.isNotBlank()) { "BoardId must not be blank" } }
    companion object { fun generate(): BoardId = BoardId(UUID.randomUUID().toString()) }
}
