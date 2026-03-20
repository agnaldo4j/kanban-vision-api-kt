package com.kanbanvision.domain.model

import java.util.UUID

data class Tenant(
    val id: String,
    val name: String,
    val audit: Audit = Audit(),
) {
    init {
        require(id.isNotBlank()) { "Tenant id must not be blank" }
        require(name.isNotBlank()) { "Tenant name must not be blank" }
    }

    companion object {
        fun create(name: String): Tenant {
            require(name.isNotBlank()) { "Tenant name must not be blank" }
            return Tenant(id = UUID.randomUUID().toString(), name = name)
        }
    }
}
