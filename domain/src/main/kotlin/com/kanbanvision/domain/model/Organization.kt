package com.kanbanvision.domain.model

import java.util.UUID

data class Organization(
    override val id: String,
    val name: String,
    val tribes: List<Tribe> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Organization id must not be blank" }
        require(name.isNotBlank()) { "Organization name must not be blank" }
    }

    companion object {
        fun create(
            name: String,
            tribes: List<Tribe> = emptyList(),
        ): Organization {
            require(name.isNotBlank()) { "Organization name must not be blank" }
            return Organization(id = UUID.randomUUID().toString(), name = name, tribes = tribes)
        }
    }
}
