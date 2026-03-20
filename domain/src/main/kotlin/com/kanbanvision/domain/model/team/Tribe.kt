package com.kanbanvision.domain.model.team

data class Tribe(
    val name: String,
    val squads: List<Squad>,
) {
    init {
        require(name.isNotBlank()) { "Tribe name must not be blank" }
    }
}
