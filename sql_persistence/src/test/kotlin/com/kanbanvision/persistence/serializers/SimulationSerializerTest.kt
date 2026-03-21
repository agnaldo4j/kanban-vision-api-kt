package com.kanbanvision.persistence.serializers

import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `given encoded simulation payload when adding unknown field then decode ignores unknown keys`() {
        val source = PersistenceFixtures.simulation()
        val raw = SimulationSerializer.encode(source)
        val withUnknown = raw.dropLast(1) + """, "unknown": "value"}"""

        val decoded = SimulationSerializer.decode(withUnknown)

        assertEquals(source.id, decoded.id)
        assertTrue(decoded.organization.tribes.isNotEmpty())
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
