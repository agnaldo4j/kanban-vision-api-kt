package com.kanbanvision.httpapi.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ValidationErrorResponse(
    val errors: List<String>,
    val requestId: String,
)

@Serializable
data class DomainErrorResponse(
    val error: String,
    val requestId: String,
)
