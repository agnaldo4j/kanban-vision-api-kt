package com.kanbanvision.domain.model.organization

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import java.util.UUID

data class Tribe(
    override val id: String = UUID.randomUUID().toString(),
    val name: String,
    val squads: List<Squad> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "Tribe id must not be blank" }
        require(name.isNotBlank()) { "Tribe name must not be blank" }
    }
}
