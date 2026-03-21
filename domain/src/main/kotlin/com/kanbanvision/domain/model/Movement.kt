package com.kanbanvision.domain.model

import java.util.UUID

data class Movement(
    override val id: String = UUID.randomUUID().toString(),
    val type: MovementType,
    val cardId: String,
    val day: SimulationDay,
    val reason: String,
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Movement id must not be blank" }
        require(cardId.isNotBlank()) { "Movement cardId must not be blank" }
    }
}
