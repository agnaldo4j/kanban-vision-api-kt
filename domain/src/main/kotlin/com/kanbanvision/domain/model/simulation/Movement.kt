package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.CardId
import com.kanbanvision.domain.model.Domain
import java.util.UUID

data class Movement(
    override val id: String = UUID.randomUUID().toString(),
    val type: MovementType,
    val cardId: CardId,
    val day: SimulationDay,
    val reason: String,
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "Movement id must not be blank" }
    }
}
