package com.kanbanvision.usecases.ports

import com.kanbanvision.domain.simulation.events.DomainEvent

interface EventPublisherPort {
    fun publish(events: List<DomainEvent>)
}
