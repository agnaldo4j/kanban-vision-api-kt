package com.kanbanvision.usecases

data class Page<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)
