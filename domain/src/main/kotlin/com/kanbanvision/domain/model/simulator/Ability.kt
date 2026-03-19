package com.kanbanvision.domain.model.simulator

import java.time.Instant
import java.util.UUID

data class Ability(
    val id: String = UUID.randomUUID().toString(),
    val name: AbilityName,
    val seniority: Seniority,
    val createdDate: Instant = Instant.now(),
    val updatedDate: Instant = createdDate,
    val deletedDate: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "Ability id must not be blank" }
        require(!updatedDate.isBefore(createdDate)) { "Ability updatedDate must be equal or after createdDate" }
        require(deletedDate == null || !deletedDate.isBefore(createdDate)) {
            "Ability deletedDate must be equal or after createdDate when provided"
        }
    }
}
