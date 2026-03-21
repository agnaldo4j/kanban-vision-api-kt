package com.kanbanvision.domain.model

import com.kanbanvision.domain.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DataClassContractsAndFactoryGuardsTest {
    @Test
    fun `given aggregate data classes when copying then equality and hash contract remains stable`() {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 3, seedValue = 7L)
        val board = Board.create("Board").addStep("Analysis", AbilityName.PRODUCT_MANAGER)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
        val simulation = Simulation.create(name = "Sim", organization = Organization.create("Org"), scenario = scenario)
        val snapshot =
            DailySnapshot(
                simulationId = simulation.id,
                day = SimulationDay(1),
                metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 1.0),
                movements = listOf(Movement(type = MovementType.MOVED, cardId = "card-1", day = SimulationDay(1), reason = "move")),
            )

        val sameScenario = scenario.copy()
        val changedScenario = scenario.copy(name = "Scenario 2")

        assertEquals(scenario, sameScenario)
        assertEquals(scenario.hashCode(), sameScenario.hashCode())
        assertNotEquals(scenario, changedScenario)
        assertEquals(simulation.id, snapshot.simulationId)
    }

    @Test
    fun `given value objects when destructuring and copying then values remain consistent`() {
        val movement = Movement(type = MovementType.BLOCKED, cardId = "card-1", day = SimulationDay(2), reason = "blocked")
        val flow = FlowMetrics(throughput = 3, wipCount = 4, blockedCount = 1, avgAgingDays = 2.5)
        val policySet = PolicySet(wipLimit = 5)
        val decision = Decision.addItem(title = "Task", serviceClass = "STANDARD")

        val movementCopy = movement.copy(reason = "still blocked")
        val flowCopy = flow.copy(wipCount = 5)
        val policyCopy = policySet.copy(wipLimit = 6)
        val decisionCopy = decision.copy(payload = decision.payload + ("priority" to "high"))

        assertEquals("still blocked", movementCopy.reason)
        assertEquals(5, flowCopy.wipCount)
        assertEquals(6, policyCopy.wipLimit)
        assertEquals("high", decisionCopy.payload["priority"])
    }

    @Test
    fun `given companion factories with invalid input when creating domain objects then validation guards trigger`() {
        assertFailsWith<IllegalArgumentException> { Card.create(stepId = "", title = "Task", position = 0) }
        assertFailsWith<IllegalArgumentException> { Card.create(stepId = "step-1", title = "", position = 0) }
        assertFailsWith<IllegalArgumentException> {
            Scenario.create(name = "", rules = ScenarioRules.create(1, 1, 1L), board = Board.create("B"))
        }
        assertFailsWith<IllegalArgumentException> {
            Simulation.create(name = "", organization = Organization.create("Org"), scenario = scenario())
        }
        assertFailsWith<IllegalArgumentException> { Organization.create(name = "") }
        assertFailsWith<IllegalArgumentException> { Board.create(name = "") }
    }

    @Test
    fun `given valid values when creating day already executed and validation errors then payload remains accessible`() {
        val dayError = DomainError.DayAlreadyExecuted(day = 1)
        val validation = DomainError.ValidationError(messages = listOf("a", "b"))

        assertEquals(1, dayError.day)
        assertEquals("a; b", validation.message)
    }

    @Test
    fun `given audit companion no arg now when creating then timestamps are valid and monotonic`() {
        val audit = Audit.now()
        val touched = audit.touch()

        assertEquals(audit.createdAt, audit.updatedAt)
        assertTrue(touched.updatedAt >= touched.createdAt)
    }

    private fun scenario(): Scenario {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 1L)
        return Scenario.create(name = "Scenario", rules = rules, board = Board.create("Board"))
    }
}
