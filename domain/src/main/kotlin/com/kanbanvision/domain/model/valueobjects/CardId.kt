package com.kanbanvision.domain.model.valueobjects

import java.util.UUID

@JvmInline
value class CardId(val value: String) {
    init { require(value.isNotBlank()) { "CardId must not be blank" } }
    companion object { fun generate(): CardId = CardId(UUID.randomUUID().toString()) }
}
