package com.kanbanvision.domain.model

import java.util.UUID

data class Ability(
    override val id: String = UUID.randomUUID().toString(),
    val name: AbilityName,
    val seniority: Seniority,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Ability id must not be blank" }
    }
}
