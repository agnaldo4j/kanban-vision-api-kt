package com.kanbanvision.domain.model

interface Domain<ID> {
    val id: ID
    val audit: Audit
}
