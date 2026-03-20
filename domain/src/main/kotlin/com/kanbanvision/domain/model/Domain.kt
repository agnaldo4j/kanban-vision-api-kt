package com.kanbanvision.domain.model

interface Domain {
    val id: String
    val audit: Audit
}
