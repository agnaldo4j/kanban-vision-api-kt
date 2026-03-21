package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecisionSnapshotAndMetricsBehaviorTest {
    @Test
    fun `given decision factories when creating decisions then payload and type match intent`() {
        val move = Decision.move(cardId = "card-1")
        val block = Decision.block(cardId = "card-2", reason = "dependency")
        val unblock = Decision.unblock(cardId = "card-2")
        val add = Decision.addItem(title = "New item", serviceClass = "EXPEDITE")

        assertEquals(DecisionType.MOVE_ITEM, move.type)
        assertEquals("card-1", move.payload["cardId"])
        assertEquals(DecisionType.BLOCK_ITEM, block.type)
        assertEquals("dependency", block.payload["reason"])
        assertEquals(DecisionType.UNBLOCK_ITEM, unblock.type)
        assertEquals(DecisionType.ADD_ITEM, add.type)
        assertEquals("EXPEDITE", add.payload["serviceClass"])
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
                simulation = SimulationRef(""),
                scenario = ScenarioRef("scn-1"),
                day = SimulationDay(1),
                metrics = metrics,
                movements = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Movement(type = MovementType.MOVED, cardId = "", day = SimulationDay(1), reason = "invalid")
        }
    }

    @Test
    fun `given invalid simulation day when constructing then minimum day constraint is enforced`() {
        assertFailsWith<IllegalArgumentException> {
            SimulationDay(0)
        }
    }
}
