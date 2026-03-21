package com.kanbanvision.domain.model

import java.util.UUID

data class Tribe(
    override val id: String = UUID.randomUUID().toString(),
    val name: String,
    val squads: List<Squad> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Tribe id must not be blank" }
        require(name.isNotBlank()) { "Tribe name must not be blank" }
    }
}
