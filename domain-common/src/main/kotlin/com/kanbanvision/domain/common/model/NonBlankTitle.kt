package com.kanbanvision.domain.common.model

@JvmInline
value class NonBlankTitle(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "title must not be blank" }
    }
}
