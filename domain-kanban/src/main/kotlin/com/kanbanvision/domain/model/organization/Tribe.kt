package com.kanbanvision.domain.model.organization

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.common.model.NonBlankName
import java.util.UUID

data class Tribe(
    override val id: String = UUID.randomUUID().toString(),
    val name: NonBlankName,
    val squads: List<Squad> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "Tribe id must not be blank" }
    }
}
