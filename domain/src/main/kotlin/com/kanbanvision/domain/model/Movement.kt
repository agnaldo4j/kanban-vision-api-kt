package com.kanbanvision.domain.model

data class Movement(
    val type: MovementType,
    val cardId: String,
    val day: SimulationDay,
    val reason: String,
    val audit: Audit = Audit(),
) {
    init {
        require(cardId.isNotBlank()) { "Movement cardId must not be blank" }
    }
}
