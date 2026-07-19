package com.kanbanvision.domain.common.model

interface Domain<ID> {
    val id: ID
    val audit: Audit
}
