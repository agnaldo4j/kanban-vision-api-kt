package com.kanbanvision.domain.event

import com.kanbanvision.domain.model.valueobjects.BoardId
import java.time.Instant

data class BoardCreated(
    val boardId: BoardId,
    val name: String,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
