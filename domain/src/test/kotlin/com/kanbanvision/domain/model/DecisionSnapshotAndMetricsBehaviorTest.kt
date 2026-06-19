package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.SimulationDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DecisionSnapshotAndMetricsBehaviorTest {
    @Test
    fun `given decision subtypes when creating decisions then fields match intent`() {
        val move = Decision.MoveItem(cardId = "card-1")
        val block = Decision.BlockItem(cardId = "card-2", reason = "dependency")
        val unblock = Decision.UnblockItem(cardId = "card-2")
        val add = Decision.AddItem(title = "New item", serviceClass = ServiceClass.EXPEDITE)

        assertIs<Decision.MoveItem>(move)
        assertEquals("card-1", move.cardId)
        assertIs<Decision.BlockItem>(block)
        assertEquals("dependency", block.reason)
        assertIs<Decision.UnblockItem>(unblock)
        assertIs<Decision.AddItem>(add)
        assertEquals(ServiceClass.EXPEDITE, add.serviceClass)
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
