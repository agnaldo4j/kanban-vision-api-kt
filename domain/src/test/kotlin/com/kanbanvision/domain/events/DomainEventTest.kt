package com.kanbanvision.domain.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals("sim-1", event.simulationId)
        assertEquals(5, event.day)
        assertEquals(3, event.throughput)
        assertEquals(2, event.wipCount)
        assertEquals(1, event.blockedCount)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `CardCompleted carries simulationId cardId and day`() {
        val event = DomainEvent.CardCompleted("sim-1", "card-42", day = 3)

        assertIs<DomainEvent.CardCompleted>(event)
        assertEquals("sim-1", event.simulationId)
        assertEquals("card-42", event.cardId)
        assertEquals(3, event.day)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `CardBlocked carries all fields`() {
        val event = DomainEvent.CardBlocked("sim-1", "card-7", day = 2, reason = "external dependency")

        assertIs<DomainEvent.CardBlocked>(event)
        assertEquals("sim-1", event.simulationId)
        assertEquals("card-7", event.cardId)
        assertEquals(2, event.day)
        assertEquals("external dependency", event.reason)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `CardMoved carries simulationId cardId and day`() {
        val event = DomainEvent.CardMoved("sim-1", "card-3", day = 1)

        assertIs<DomainEvent.CardMoved>(event)
        assertEquals("sim-1", event.simulationId)
        assertEquals("card-3", event.cardId)
        assertEquals(1, event.day)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `CardUnblocked carries simulationId cardId and day`() {
        val event = DomainEvent.CardUnblocked("sim-1", "card-5", day = 4)

        assertIs<DomainEvent.CardUnblocked>(event)
        assertEquals("sim-1", event.simulationId)
        assertEquals("card-5", event.cardId)
        assertEquals(4, event.day)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `CardMoved equals and copy work correctly`() {
        val e1 = DomainEvent.CardMoved("sim-1", "card-1", day = 1)
        val e2 = e1.copy()
        val e3 = e1.copy(day = 2)

        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
        assertTrue(e1 != e3)
        assertTrue(e1.toString().contains("CardMoved"))
    }

    @Test
    fun `CardBlocked equals and copy work correctly`() {
        val e1 = DomainEvent.CardBlocked("sim-1", "card-2", day = 3, reason = "dep")
        val e2 = e1.copy()
        val e3 = e1.copy(reason = "other")

        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
        assertTrue(e1 != e3)
        assertTrue(e1.toString().contains("CardBlocked"))
    }

    @Test
    fun `SimulationDayExecuted equals and copy work correctly`() {
        val e1 = DomainEvent.SimulationDayExecuted("sim-1", day = 5, throughput = 3, wipCount = 2, blockedCount = 1)
        val e2 = e1.copy()
        val e3 = e1.copy(throughput = 0)

        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
        assertTrue(e1 != e3)
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
                DomainEvent.CardUnblocked("s", "c", 1),
            )

        assertEquals(6, events.size)
        events.forEach { assertIs<DomainEvent>(it) }
    }
}
