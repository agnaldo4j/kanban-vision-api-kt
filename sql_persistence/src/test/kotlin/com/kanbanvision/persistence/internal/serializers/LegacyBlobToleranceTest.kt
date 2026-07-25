package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.persistence.support.PersistenceFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * GAP-DS: the persisted blobs are an immutable read record written by earlier — or newer, then
 * rolled back — releases. Decoding one must never throw, because `SimulationSurrogate.toDomain`
 * maps eagerly: a single unreadable decision or movement would abort the whole aggregate
 * (`PersistenceError` → 500 on `findById` and on an entire `findAllByOrganization` page).
 *
 * These tests pin the tolerance *and* the fidelity: what cannot be interpreted is preserved, not
 * dropped, so it round-trips back to storage unchanged instead of being erased on the next save.
 */
class LegacyBlobToleranceTest {
    // --- decisions -------------------------------------------------------------------------

    @Test
    fun `legacy decision with an unknown type decodes without crashing the load`() {
        val surrogate = DecisionSurrogate(type = "FUTURE_KIND", payload = mapOf("cardId" to "c-9", "extra" to "x"))

        val decoded = surrogate.toDomain()

        val unknown = assertIs<Decision.Unknown>(decoded)
        assertEquals("FUTURE_KIND", unknown.type)
        assertEquals(mapOf("cardId" to "c-9", "extra" to "x"), unknown.payload)
    }

    @Test
    fun `unknown decision round-trips back to storage unchanged`() {
        val surrogate = DecisionSurrogate(type = "FUTURE_KIND", payload = mapOf("cardId" to "c-9"))

        val reEncoded = surrogate.toDomain().toSurrogate()

        assertEquals(surrogate, reEncoded)
    }

    @Test
    fun `legacy decision with a missing cardId decodes to unknown instead of crashing the load`() {
        val decoded = DecisionSurrogate(type = "MOVE_ITEM", payload = emptyMap()).toDomain()

        val unknown = assertIs<Decision.Unknown>(decoded)
        assertEquals("MOVE_ITEM", unknown.type)
        assertTrue(unknown.payload.isEmpty())
    }

    @Test
    fun `legacy decision with a blank cardId decodes to unknown instead of crashing the load`() {
        listOf("BLOCK_ITEM", "UNBLOCK_ITEM", "MOVE_ITEM").forEach { type ->
            val decoded = DecisionSurrogate(type = type, payload = mapOf("cardId" to " ")).toDomain()

            val unknown = assertIs<Decision.Unknown>(decoded, "type=$type")
            assertEquals(type, unknown.type)
        }
    }

    @Test
    fun `legacy add-item with a missing title decodes to the sentinel instead of crashing the load`() {
        val decoded = DecisionSurrogate(type = "ADD_ITEM", payload = emptyMap()).toDomain()

        val addItem = assertIs<Decision.AddItem>(decoded)
        assertEquals("(untitled)", addItem.title.value)
    }

    @Test
    fun `a whole simulation blob carrying an undecodable decision still loads`() {
        val encoded =
            SimulationSerializer
                .encode(PersistenceFixtures.simulation())
                .replace(""""type":"MOVE_ITEM"""", """"type":"CORRUPTED"""")
        assertTrue(encoded.contains("CORRUPTED"), "the corrupted blob must actually differ")

        val decoded = SimulationSerializer.decode(encoded)

        assertEquals(PersistenceFixtures.simulation().id, decoded.id)
        assertIs<Decision.Unknown>(decoded.decisions.first())
        assertTrue(
            decoded.scenario.board.steps
                .isNotEmpty(),
            "the rest of the aggregate must survive",
        )
    }

    // --- movements -------------------------------------------------------------------------

    @Test
    fun `legacy movement with an unknown type decodes without crashing the load in the state blob`() {
        val encoded =
            SimulationSerializer
                .encode(PersistenceFixtures.simulation())
                .replace(""""type":"MOVED"""", """"type":"TELEPORTED"""")
        assertNotEquals(SimulationSerializer.encode(PersistenceFixtures.simulation()), encoded)

        val movement =
            SimulationSerializer
                .decode(encoded)
                .history
                .first()
                .movements
                .first()

        assertEquals(MovementType.Unknown("TELEPORTED"), movement.type)
        assertEquals("TELEPORTED", movement.type.tag)
    }

    @Test
    fun `legacy movement with an unknown type decodes without crashing the load in the snapshot blob`() {
        val snapshot = PersistenceFixtures.snapshot()
        val encoded = DailySnapshotSerializer.encode(snapshot).replace(""""type":"MOVED"""", """"type":"TELEPORTED"""")

        val decoded = DailySnapshotSerializer.decode(encoded)

        assertEquals(MovementType.Unknown("TELEPORTED"), decoded.movements.first().type)
    }

    @Test
    fun `unknown movement type round-trips its original tag back to storage`() {
        val snapshot = PersistenceFixtures.snapshot()
        val legacy = DailySnapshotSerializer.encode(snapshot).replace(""""type":"MOVED"""", """"type":"TELEPORTED"""")

        val reEncoded = DailySnapshotSerializer.encode(DailySnapshotSerializer.decode(legacy))

        assertEquals(legacy, reEncoded)
    }

    @Test
    fun `legacy movement with a blank cardId decodes to a sentinel instead of crashing the load`() {
        val snapshot = PersistenceFixtures.snapshot()
        val cardId =
            snapshot.movements
                .first()
                .cardId.value
        val encoded = DailySnapshotSerializer.encode(snapshot).replace(""""cardId":"$cardId"""", """"cardId":""""")

        val decoded = DailySnapshotSerializer.decode(encoded)

        assertEquals(
            "(unknown)",
            decoded.movements
                .first()
                .cardId.value,
        )
    }

    @Test
    fun `known movement tags keep the exact wire value the enum used to emit`() {
        listOf(MovementType.MOVED, MovementType.BLOCKED, MovementType.UNBLOCKED, MovementType.COMPLETED)
            .forEach { assertEquals(it, MovementType.fromTag(it.tag)) }

        assertEquals("MOVED", MovementType.MOVED.tag)
        assertEquals("BLOCKED", MovementType.BLOCKED.tag)
        assertEquals("UNBLOCKED", MovementType.UNBLOCKED.tag)
        assertEquals("COMPLETED", MovementType.COMPLETED.tag)
    }
}
