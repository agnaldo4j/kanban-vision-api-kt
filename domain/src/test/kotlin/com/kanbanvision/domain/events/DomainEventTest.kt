package com.kanbanvision.domain.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class DomainEventTest {
    @Test
    fun `SimulationCreated carries simulationId organizationId and name`() {
        val event = DomainEvent.SimulationCreated("sim-1", "My Simulation", "org-1")

        assertIs<DomainEvent.SimulationCreated>(event)
        assertEquals("sim-1", event.simulationId)
        assertEquals("My Simulation", event.simulationName)
        assertEquals("org-1", event.organizationId)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `SimulationDayExecuted carries all flow metrics`() {
        val event = DomainEvent.SimulationDayExecuted("sim-1", day = 5, throughput = 3, wipCount = 2, blockedCount = 1)

        assertIs<DomainEvent.SimulationDayExecuted>(event)
        assertEquals(5, event.day)
        assertEquals(3, event.throughput)
        assertEquals(2, event.wipCount)
        assertEquals(1, event.blockedCount)
    }

    @Test
    fun `CardCompleted carries simulationId cardId and day`() {
        val event = DomainEvent.CardCompleted("sim-1", "card-42", day = 3)

        assertIs<DomainEvent.CardCompleted>(event)
        assertEquals("sim-1", event.simulationId)
        assertEquals("card-42", event.cardId)
        assertEquals(3, event.day)
    }

    @Test
    fun `CardBlocked carries reason`() {
        val event = DomainEvent.CardBlocked("sim-1", "card-7", day = 2, reason = "external dependency")

        assertIs<DomainEvent.CardBlocked>(event)
        assertEquals("external dependency", event.reason)
    }

    @Test
    fun `CardMoved carries simulationId cardId and day`() {
        val event = DomainEvent.CardMoved("sim-1", "card-3", day = 1)

        assertIs<DomainEvent.CardMoved>(event)
        assertEquals(1, event.day)
    }

    @Test
    fun `all event subtypes are DomainEvent`() {
        val events: List<DomainEvent> =
            listOf(
                DomainEvent.SimulationCreated("s", "n", "o"),
                DomainEvent.SimulationDayExecuted("s", 1, 0, 0, 0),
                DomainEvent.CardCompleted("s", "c", 1),
                DomainEvent.CardBlocked("s", "c", 1, "r"),
                DomainEvent.CardMoved("s", "c", 1),
            )

        assertEquals(5, events.size)
        events.forEach { assertIs<DomainEvent>(it) }
    }
}
