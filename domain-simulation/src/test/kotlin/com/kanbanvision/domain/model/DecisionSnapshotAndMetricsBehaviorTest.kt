package com.kanbanvision.domain.model

import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DecisionSnapshotAndMetricsBehaviorTest {
    @Test
    fun `given decision subtypes when creating decisions then fields match intent`() {
        val move = Decision.MoveItem(cardId = CardId("card-1"))
        val block = Decision.BlockItem(cardId = CardId("card-2"), reason = "dependency")
        val unblock = Decision.UnblockItem(cardId = CardId("card-2"))
        val add = Decision.AddItem(title = NonBlankTitle("New item"), serviceClass = ServiceClass.EXPEDITE)

        assertIs<Decision.MoveItem>(move)
        assertEquals("card-1", move.cardId.value)
        assertIs<Decision.BlockItem>(block)
        assertEquals("dependency", block.reason)
        assertIs<Decision.UnblockItem>(unblock)
        assertIs<Decision.AddItem>(add)
        assertEquals(ServiceClass.EXPEDITE, add.serviceClass)
    }

    @Test
    fun `given an uninterpretable persisted decision when creating unknown then type and payload are carried verbatim`() {
        val unknown = Decision.Unknown(type = "FUTURE_KIND", payload = mapOf("cardId" to "card-9"))

        assertEquals("FUTURE_KIND", unknown.type)
        assertEquals(mapOf("cardId" to "card-9"), unknown.payload)
    }

    @Test
    fun `given movement tags when decoding then known tags resolve and anything else becomes unknown`() {
        assertEquals(MovementType.MOVED, MovementType.fromTag("MOVED"))
        assertEquals(MovementType.BLOCKED, MovementType.fromTag("BLOCKED"))
        assertEquals(MovementType.UNBLOCKED, MovementType.fromTag("UNBLOCKED"))
        assertEquals(MovementType.COMPLETED, MovementType.fromTag("COMPLETED"))

        val unknown = assertIs<MovementType.Unknown>(MovementType.fromTag("TELEPORTED"))
        assertEquals("TELEPORTED", unknown.tag)
        // The tag is the wire representation: it must survive verbatim so a re-encode is lossless.
        assertEquals("TELEPORTED", MovementType.fromTag("TELEPORTED").tag)
    }

    @Test
    fun `given invalid flow metric values when constructing metrics then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = -1, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = 0, wipCount = -1, blockedCount = 0, avgAgingDays = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = 0, wipCount = 0, blockedCount = -1, avgAgingDays = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = -0.1)
        }
    }

    @Test
    fun `given invalid snapshot and movement identifiers when constructing then validation fails`() {
        val metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)

        assertFailsWith<IllegalArgumentException> {
            DailySnapshot(
                simulation = SimulationId(""),
                scenario = ScenarioId("scn-1"),
                day = SimulationDay(1),
                metrics = metrics,
                movements = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Movement(type = MovementType.MOVED, cardId = CardId(""), day = SimulationDay(1), reason = "invalid")
        }
    }

    @Test
    fun `given invalid simulation day when constructing then minimum day constraint is enforced`() {
        assertFailsWith<IllegalArgumentException> {
            SimulationDay(0)
        }
    }
}
