package com.kanbanvision.domain.model.team

data class Squad(
    val name: String,
    val workers: List<Worker>,
) {
    init {
        require(name.isNotBlank()) { "Squad name must not be blank" }
    }
}
