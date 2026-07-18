package com.kanbanvision.httpapi.events

import com.kanbanvision.domain.simulation.events.DomainEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class MicrometerEventPublisherTest {
    private val registry = SimpleMeterRegistry()
    private val publisher = MicrometerEventPublisher(registry)

    @Test
    fun `given SimulationCreated event when published then kanban_simulation_created counter is incremented`() {
        publisher.publish(listOf(DomainEvent.SimulationCreated("sim-1", "My Sim", "org-1")))

        assertEquals(1.0, registry.get("kanban.simulation.created").counter().count())
    }

    @Test
    fun `given SimulationDayExecuted event when published then kanban_simulation_days_executed counter is incremented`() {
        publisher.publish(listOf(DomainEvent.SimulationDayExecuted("sim-1", day = 1, throughput = 2, wipCount = 3, blockedCount = 0)))
        publisher.publish(listOf(DomainEvent.SimulationDayExecuted("sim-1", day = 2, throughput = 1, wipCount = 2, blockedCount = 1)))

        assertEquals(2.0, registry.get("kanban.simulation.days.executed").counter().count())
    }

    @Test
    fun `given CardCompleted event when published then kanban_card_completed counter is incremented`() {
        publisher.publish(listOf(DomainEvent.CardCompleted("sim-1", "card-1", day = 1)))

        assertEquals(1.0, registry.get("kanban.card.completed").counter().count())
    }

    @Test
    fun `given CardBlocked event when published then kanban_card_blocked counter is incremented`() {
        publisher.publish(listOf(DomainEvent.CardBlocked("sim-1", "card-2", day = 1, reason = "dependency")))

        assertEquals(1.0, registry.get("kanban.card.blocked").counter().count())
    }

    @Test
    fun `given CardMoved event when published then kanban_card_moved counter is incremented`() {
        publisher.publish(listOf(DomainEvent.CardMoved("sim-1", "card-3", day = 1)))

        assertEquals(1.0, registry.get("kanban.card.moved").counter().count())
    }

    @Test
    fun `given CardUnblocked event when published then kanban_card_unblocked counter is incremented`() {
        publisher.publish(listOf(DomainEvent.CardUnblocked("sim-1", "card-4", day = 2)))

        assertEquals(1.0, registry.get("kanban.card.unblocked").counter().count())
    }

    @Test
    fun `given mixed events when published then each counter reflects only its event type`() {
        publisher.publish(
            listOf(
                DomainEvent.SimulationDayExecuted("sim-1", day = 1, throughput = 1, wipCount = 1, blockedCount = 0),
                DomainEvent.CardCompleted("sim-1", "card-1", day = 1),
                DomainEvent.CardMoved("sim-1", "card-2", day = 1),
                DomainEvent.CardBlocked("sim-1", "card-3", day = 1, reason = "blocked"),
                DomainEvent.CardUnblocked("sim-1", "card-3", day = 2),
            ),
        )

        assertEquals(1.0, registry.get("kanban.simulation.days.executed").counter().count())
        assertEquals(1.0, registry.get("kanban.card.completed").counter().count())
        assertEquals(1.0, registry.get("kanban.card.moved").counter().count())
        assertEquals(1.0, registry.get("kanban.card.blocked").counter().count())
        assertEquals(1.0, registry.get("kanban.card.unblocked").counter().count())
    }
}
