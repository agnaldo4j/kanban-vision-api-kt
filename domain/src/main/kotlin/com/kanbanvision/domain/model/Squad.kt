package com.kanbanvision.domain.model

import java.util.UUID

data class Squad(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val workers: List<Worker>,
    val audit: Audit = Audit(),
) {
    val createdDate get() = audit.createdAt
    val updatedDate get() = audit.updatedAt
    val deletedDate get() = audit.deletedAt

    init {
        require(id.isNotBlank()) { "Squad id must not be blank" }
        require(name.isNotBlank()) { "Squad name must not be blank" }
    }
}
