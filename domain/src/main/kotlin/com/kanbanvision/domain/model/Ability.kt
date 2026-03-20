package com.kanbanvision.domain.model

import java.util.UUID

data class Ability(
    val id: String = UUID.randomUUID().toString(),
    val name: AbilityName,
    val seniority: Seniority,
    val audit: Audit = Audit(),
) {
    val createdDate get() = audit.createdAt
    val updatedDate get() = audit.updatedAt
    val deletedDate get() = audit.deletedAt

    init {
        require(id.isNotBlank()) { "Ability id must not be blank" }
    }
}
