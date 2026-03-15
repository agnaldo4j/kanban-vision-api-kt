package com.kanbanvision.domain.model.tenant

import com.kanbanvision.domain.model.valueobjects.TenantId

data class Tenant(
    val id: TenantId,
    val name: String,
) {
    companion object {
        fun create(name: String): Tenant {
            require(name.isNotBlank()) { "Tenant name must not be blank" }
            return Tenant(id = TenantId.generate(), name = name)
        }
    }
}
