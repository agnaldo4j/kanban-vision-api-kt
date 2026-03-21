package com.kanbanvision.domain.model

import java.util.UUID

data class PolicySet(
    override val id: String = UUID.randomUUID().toString(),
    val wipLimit: Int,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "PolicySet id must not be blank" }
        require(wipLimit > 0) { "WIP limit must be greater than zero" }
    }
}
