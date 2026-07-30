package com.kanbanvision.domain.common.model

@JvmInline
value class NonBlankName(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "name must not be blank" }
    }
}
