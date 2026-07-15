package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulationSerializerTest {
    @Test
    fun `given rich simulation aggregate when encoding and decoding then full structure is preserved`() {
        val source = PersistenceFixtures.simulation()

        val encoded = SimulationSerializer.encode(source)
        val decoded = SimulationSerializer.decode(encoded)

        assertCoreSimulationFields(source, decoded)
        assertNestedOrganizationFields(source, decoded)
        assertNestedCardFields(source, decoded)
    }

    @Test
    fun `given simulation with block item decision when encoding and decoding then decision is preserved`() {
        val base = PersistenceFixtures.simulation()
        val decision = Decision.BlockItem(cardId = "c-2", reason = "waiting")
        val source = base.copy(decisions = listOf(decision))

        val decoded = SimulationSerializer.decode(SimulationSerializer.encode(source))

        val restored = assertIs<Decision.BlockItem>(decoded.decisions.first())
        assertEquals("c-2", restored.cardId)
        assertEquals("waiting", restored.reason)
    }

    @Test
    fun `given simulation with unblock item decision when encoding and decoding then decision is preserved`() {
        val base = PersistenceFixtures.simulation()
        val decision = Decision.UnblockItem(cardId = "c-3")
        val source = base.copy(decisions = listOf(decision))

        val decoded = SimulationSerializer.decode(SimulationSerializer.encode(source))

        val restored = assertIs<Decision.UnblockItem>(decoded.decisions.first())
        assertEquals("c-3", restored.cardId)
    }

    @Test
    fun `given simulation with add item decision when encoding and decoding then decision and service class are preserved`() {
        val base = PersistenceFixtures.simulation()
        val decision = Decision.AddItem(title = "Fast track", serviceClass = ServiceClass.EXPEDITE)
        val source = base.copy(decisions = listOf(decision))

        val decoded = SimulationSerializer.decode(SimulationSerializer.encode(source))

        val restored = assertIs<Decision.AddItem>(decoded.decisions.first())
        assertEquals("Fast track", restored.title)
        assertEquals(ServiceClass.EXPEDITE, restored.serviceClass)
    }

    @Test
    fun `given simulation with add item default service class when encoding and decoding then standard is preserved`() {
        val base = PersistenceFixtures.simulation()
        val decision = Decision.AddItem(title = "Backlog item")
        val source = base.copy(decisions = listOf(decision))

        val decoded = SimulationSerializer.decode(SimulationSerializer.encode(source))

        val restored = assertIs<Decision.AddItem>(decoded.decisions.first())
        assertEquals(ServiceClass.STANDARD, restored.serviceClass)
    }

    @Test
    fun `given encoded block item with reason removed when decoding then default reason is used`() {
        val base = PersistenceFixtures.simulation()
        val decision = Decision.BlockItem(cardId = "c-2", reason = "dep")
        val source = base.copy(decisions = listOf(decision))
        val encoded = SimulationSerializer.encode(source).replace(""","reason":"dep"""", "")

        val decoded = SimulationSerializer.decode(encoded)

        val restored = assertIs<Decision.BlockItem>(decoded.decisions.first())
        assertEquals("blocked", restored.reason)
    }

    @Test
    fun `given encoded add item with invalid service class when decoding then standard is used as fallback`() {
        val base = PersistenceFixtures.simulation()
        val decision = Decision.AddItem(title = "UniqueMarker-Title")
        val source = base.copy(decisions = listOf(decision))
        val encoded =
            SimulationSerializer.encode(source).replace(
                """"title":"UniqueMarker-Title","serviceClass":"STANDARD"""",
                """"title":"UniqueMarker-Title","serviceClass":"INVALID_CLASS"""",
            )

        val decoded = SimulationSerializer.decode(encoded)

        val restored = assertIs<Decision.AddItem>(decoded.decisions.first())
        assertEquals(ServiceClass.STANDARD, restored.serviceClass)
    }

    @Test
    fun `given stored decision with missing payload field when decoding then error is thrown for missing key`() {
        val encoded =
            SimulationSerializer
                .encode(PersistenceFixtures.simulation())
                .replace("""{"type":"MOVE_ITEM","payload":{"cardId":"c-1"}}""", """{"type":"MOVE_ITEM"}""")
        assertFailsWith<IllegalStateException> { SimulationSerializer.decode(encoded) }
    }

    @Test
    fun `given stored decision with unknown type when decoding then error is thrown`() {
        val encoded =
            SimulationSerializer
                .encode(PersistenceFixtures.simulation())
                .replace(""""type":"MOVE_ITEM"""", """"type":"CORRUPTED"""")
        assertFailsWith<IllegalStateException> { SimulationSerializer.decode(encoded) }
    }

    @Test
    fun `given encoded decision with legacy id field when decoding then unknown id key is ignored`() {
        val base = PersistenceFixtures.simulation()
        val encoded = SimulationSerializer.encode(base)
        val withLegacyId =
            encoded.replace(
                """"type":"MOVE_ITEM"""",
                """"id":"legacy-id","type":"MOVE_ITEM"""",
            )

        val decoded = SimulationSerializer.decode(withLegacyId)

        assertIs<Decision.MoveItem>(decoded.decisions.first())
    }

    @Test
    fun `given encoded simulation payload when adding unknown field then decode ignores unknown keys`() {
        val source = PersistenceFixtures.simulation()
        val raw = SimulationSerializer.encode(source)
        val withUnknown = raw.dropLast(1) + """, "unknown": "value"}"""

        val decoded = SimulationSerializer.decode(withUnknown)

        assertEquals(source.id, decoded.id)
        assertTrue(decoded.organization.tribes.isNotEmpty())
    }

    @Test
    fun `given simulation with empty decisions and history when encoding and decoding then empty lists are preserved`() {
        val source = PersistenceFixtures.simulation().copy(decisions = emptyList(), history = emptyList())

        val decoded = SimulationSerializer.decode(SimulationSerializer.encode(source))

        assertTrue(decoded.decisions.isEmpty())
        assertTrue(decoded.history.isEmpty())
    }

    private fun assertCoreSimulationFields(
        source: Simulation,
        decoded: Simulation,
    ) {
        assertEquals(source.id, decoded.id)
        assertEquals(source.name, decoded.name)
        assertEquals(source.currentDay, decoded.currentDay)
        assertEquals(source.status, decoded.status)
        assertEquals(source.organization.id, decoded.organization.id)
    }

    private fun assertNestedOrganizationFields(
        source: Simulation,
        decoded: Simulation,
    ) {
        assertEquals(
            source.organization.tribes
                .first()
                .squads
                .first()
                .workers
                .first()
                .name,
            decoded.organization.tribes
                .first()
                .squads
                .first()
                .workers
                .first()
                .name,
        )
    }

    private fun assertNestedCardFields(
        source: Simulation,
        decoded: Simulation,
    ) {
        assertEquals(
            source.scenario.board.steps
                .first()
                .cards
                .first()
                .remainingDevelopmentEffort,
            decoded.scenario.board.steps
                .first()
                .cards
                .first()
                .remainingDevelopmentEffort,
        )
    }
}
