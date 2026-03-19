package com.kanbanvision.domain.model.simulator

import java.time.Instant
import java.util.UUID

data class Squad(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val workers: List<Worker>,
    val createdDate: Instant = Instant.now(),
    val updatedDate: Instant = createdDate,
    val deletedDate: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "Squad id must not be blank" }
        require(name.isNotBlank()) { "Squad name must not be blank" }
        require(!updatedDate.isBefore(createdDate)) { "Squad updatedDate must be equal or after createdDate" }
        require(deletedDate == null || !deletedDate.isBefore(createdDate)) {
            "Squad deletedDate must be equal or after createdDate when provided"
        }
    }
}
