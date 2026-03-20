package com.kanbanvision.domain.model.simulator

import java.time.Instant
import java.util.UUID

data class Tribe(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val squads: List<Squad>,
    val createdDate: Instant = Instant.now(),
    val updatedDate: Instant = createdDate,
    val deletedDate: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "Tribe id must not be blank" }
        require(name.isNotBlank()) { "Tribe name must not be blank" }
        require(!updatedDate.isBefore(createdDate)) { "Tribe updatedDate must be equal or after createdDate" }
        require(deletedDate == null || !deletedDate.isBefore(createdDate)) {
            "Tribe deletedDate must be equal or after createdDate when provided"
        }
    }
}
