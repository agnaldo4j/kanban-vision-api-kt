package com.kanbanvision.domain.event

import java.time.Instant

sealed interface DomainEvent {
    val occurredAt: Instant
}
