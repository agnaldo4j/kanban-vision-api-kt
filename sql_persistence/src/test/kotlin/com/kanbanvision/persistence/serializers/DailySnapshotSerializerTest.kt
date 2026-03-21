package com.kanbanvision.persistence.serializers

import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailySnapshotSerializerTest {
    @Test
    fun `given daily snapshot when encoding and decoding then movement and metrics values are preserved`() {
        val source = PersistenceFixtures.snapshot()

        val encoded = DailySnapshotSerializer.encode(source)
        val decoded = DailySnapshotSerializer.decode(encoded)

        assertEquals(source.id, decoded.id)
        assertEquals(source.simulation.id, decoded.simulation.id)
        assertEquals(source.scenario.id, decoded.scenario.id)
        assertEquals(source.day, decoded.day)
        assertEquals(source.metrics.avgAgingDays, decoded.metrics.avgAgingDays)
        assertEquals(source.movements.first().type, decoded.movements.first().type)
    }

    @Test
    fun `given encoded snapshot payload when adding unknown field then decode ignores unknown keys`() {
        val source = PersistenceFixtures.snapshot()
        val raw = DailySnapshotSerializer.encode(source)
        val withUnknown = raw.dropLast(1) + """, "unknownField": true}"""

        val decoded = DailySnapshotSerializer.decode(withUnknown)

        assertEquals(source.simulation.id, decoded.simulation.id)
        assertEquals(source.scenario.id, decoded.scenario.id)
        assertTrue(decoded.movements.isNotEmpty())
    }
}
