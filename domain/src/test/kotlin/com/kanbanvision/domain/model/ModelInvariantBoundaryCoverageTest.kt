package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelInvariantBoundaryCoverageTest {
    @Test
    fun `given id based entities when id is blank then construction fails`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(id = "", throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            DailySnapshot(
                id = "",
                simulationId = "sim-1",
                day = SimulationDay(1),
                metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
                movements = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Movement(id = "", type = MovementType.MOVED, cardId = "card-1", day = SimulationDay(1), reason = "ok")
        }
        assertFailsWith<IllegalArgumentException> { PolicySet(id = "", wipLimit = 1) }
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(id = "", policySet = PolicySet(wipLimit = 1), wipLimit = 1, teamSize = 1, seedValue = 1L)
        }
        assertFailsWith<IllegalArgumentException> { Tribe(id = "", name = "Tribe") }
        assertFailsWith<IllegalArgumentException> { Squad(id = "", name = "Squad") }
        assertFailsWith<IllegalArgumentException> {
            Decision(id = "", type = DecisionType.MOVE_ITEM, payload = mapOf("cardId" to "card-1"))
        }
    }

    @Test
    fun `given scenario companion defaults when creating without board then default board is generated`() {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 8L)

        val scenario = Scenario.create(name = "Scenario", rules = rules)

        assertEquals("Main Board", scenario.board.name)
        assertTrue(scenario.decisions.isEmpty())
        assertTrue(scenario.history.isEmpty())
    }

    @Test
    fun `given decision helper defaults when creating block and add item then default payload values are present`() {
        val blocked = Decision.block(cardId = "card-1")
        val added = Decision.addItem(title = "Task")

        assertEquals("blocked", blocked.payload["reason"])
        assertEquals("STANDARD", added.payload["serviceClass"])
    }

    @Test
    fun `given organization tribe and squad names when blank then model rejects invalid topology nodes`() {
        assertFailsWith<IllegalArgumentException> { Organization(id = "org-1", name = "") }
        assertFailsWith<IllegalArgumentException> { Tribe(id = "tribe-1", name = "") }
        assertFailsWith<IllegalArgumentException> { Squad(id = "squad-1", name = "") }
    }
}
