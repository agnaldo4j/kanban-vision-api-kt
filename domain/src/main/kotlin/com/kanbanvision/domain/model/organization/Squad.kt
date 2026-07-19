package com.kanbanvision.domain.model.organization

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.model.kanban.Worker
import java.util.UUID

data class Squad(
    override val id: String = UUID.randomUUID().toString(),
    val name: String,
    val workers: List<Worker> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<String> {
    init {
        require(id.isNotBlank()) { "Squad id must not be blank" }
        require(name.isNotBlank()) { "Squad name must not be blank" }
    }
}
