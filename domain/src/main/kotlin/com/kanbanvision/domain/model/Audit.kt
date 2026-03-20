package com.kanbanvision.domain.model

import java.time.Instant

data class Audit(
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
    val deletedAt: Instant? = null,
) {
    init {
        require(!updatedAt.isBefore(createdAt)) { "Audit updatedAt must be equal or after createdAt" }
        require(deletedAt == null || !deletedAt.isBefore(createdAt)) {
            "Audit deletedAt must be equal or after createdAt when provided"
        }
    }

    companion object {
        fun now(at: Instant = Instant.now()): Audit = Audit(createdAt = at)
    }

    fun touch(at: Instant = Instant.now()): Audit = copy(updatedAt = at)
}
