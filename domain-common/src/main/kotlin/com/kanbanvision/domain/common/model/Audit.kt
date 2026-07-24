package com.kanbanvision.domain.common.model

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
        // `at` is required (no `Instant.now()` default): the clock is an effect and must be sourced at the
        // edge, never hidden in a domain default — same rule the engine/events follow (GAP-DK / ADR-0044 spirit).
        fun now(at: Instant): Audit = Audit(createdAt = at)
    }

    fun touch(at: Instant): Audit = copy(updatedAt = at)
}
