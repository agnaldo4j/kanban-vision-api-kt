package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.ServiceClass
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.SimulationDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelInvariantBoundaryCoverageTest {
    @Test
    fun `given id based entities when id is blank then construction fails`() {
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(id = "", throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            DailySnapshot(
                id = "",
                simulation = SimulationId("sim-1"),
                scenario = ScenarioId("scn-1"),
                day = SimulationDay(1),
                metrics = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0),
                movements = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Movement(id = "", type = MovementType.MOVED, cardId = CardId("card-1"), day = SimulationDay(1), reason = "ok")
        }
        assertFailsWith<IllegalArgumentException> { PolicySet(id = "", wipLimit = 1) }
        assertFailsWith<IllegalArgumentException> {
            ScenarioRules(id = "", policySet = PolicySet(wipLimit = 1), wipLimit = 1, teamSize = 1, seedValue = 1L)
        }
        assertFailsWith<IllegalArgumentException> { Tribe(id = "", name = "Tribe") }
        assertFailsWith<IllegalArgumentException> { Squad(id = "", name = "Squad") }
    }

    @Test
    fun `given scenario companion defaults when creating without board then default board is generated`() {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 8L)

        val scenario = Scenario.create(name = "Scenario", rules = rules)

        assertEquals("Main Board", scenario.board.name)
    }

    @Test
    fun `given decision subtypes when creating block and add item then default values are present`() {
        val blocked = Decision.BlockItem(cardId = CardId("card-1"))
        val added = Decision.AddItem(title = "Task")

        assertEquals("blocked", blocked.reason)
        assertEquals(ServiceClass.STANDARD, added.serviceClass)
    }

    @Test
    fun `given organization tribe and squad names when blank then model rejects invalid topology nodes`() {
        assertFailsWith<IllegalArgumentException> { Organization(id = "org-1", name = "") }
        assertFailsWith<IllegalArgumentException> { Tribe(id = "tribe-1", name = "") }
        assertFailsWith<IllegalArgumentException> { Squad(id = "squad-1", name = "") }
    }
}
