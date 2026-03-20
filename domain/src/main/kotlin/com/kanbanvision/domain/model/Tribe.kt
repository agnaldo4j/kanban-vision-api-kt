package com.kanbanvision.domain.model

import java.util.UUID

data class Tribe(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val squads: List<Squad>,
    val audit: Audit = Audit(),
) {
    val createdDate get() = audit.createdAt
    val updatedDate get() = audit.updatedAt
    val deletedDate get() = audit.deletedAt

    init {
        require(id.isNotBlank()) { "Tribe id must not be blank" }
        require(name.isNotBlank()) { "Tribe name must not be blank" }
    }
}
