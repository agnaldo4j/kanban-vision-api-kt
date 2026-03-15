package com.kanbanvision.domain.model.valueobjects

import java.util.UUID

@JvmInline
value class TenantId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "TenantId must not be blank" }
    }

    companion object {
        fun generate(): TenantId = TenantId(UUID.randomUUID().toString())
    }
}
