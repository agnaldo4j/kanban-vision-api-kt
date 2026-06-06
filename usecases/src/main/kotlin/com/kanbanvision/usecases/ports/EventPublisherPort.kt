package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.events.DomainEvent

interface EventPublisherPort {
    fun publish(events: List<DomainEvent>)
}
