package com.kanbanvision.domain.model.organization

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.common.model.NonBlankName
import java.util.UUID

data class Organization(
    override val id: String,
    val name: NonBlankName,
    val tribes: List<Tribe> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "Organization id must not be blank" }
    }

    companion object {
        fun create(
            name: String,
            tribes: List<Tribe> = emptyList(),
        ): Organization = Organization(id = UUID.randomUUID().toString(), name = NonBlankName(name), tribes = tribes)
    }
}
