package com.kanbanvision.httpapi.events

import com.kanbanvision.domain.events.DomainEvent
import com.kanbanvision.usecases.ports.EventPublisherPort
import io.micrometer.core.instrument.MeterRegistry

class MicrometerEventPublisher(
    private val registry: MeterRegistry,
) : EventPublisherPort {
    override fun publish(events: List<DomainEvent>) {
        events.forEach { event ->
            when (event) {
                is DomainEvent.SimulationCreated ->
                    registry.counter("kanban.simulation.created").increment()
                is DomainEvent.SimulationDayExecuted ->
                    registry.counter("kanban.simulation.days.executed").increment()
                is DomainEvent.CardMoved ->
                    registry.counter("kanban.card.moved").increment()
                is DomainEvent.CardBlocked ->
                    registry.counter("kanban.card.blocked").increment()
                is DomainEvent.CardCompleted ->
                    registry.counter("kanban.card.completed").increment()
            }
        }
    }
}
