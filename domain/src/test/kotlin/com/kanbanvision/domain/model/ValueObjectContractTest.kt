package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueObjectContractTest {
    @Test
    fun `given value objects when reading component functions then contract remains consistent`() {
        val ability = Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)
        assertTrue(ability.component1().isNotBlank())
        assertEquals(AbilityName.DEVELOPER, ability.component2())
        assertEquals(Seniority.PL, ability.component3())
        assertEquals(ability.component4(), ability.copy().audit)
        assertTrue(ability.toString().contains("Ability"))
        assertEquals(ability.hashCode(), ability.copy().hashCode())

        val policy = PolicySet(wipLimit = 3)
        assertTrue(policy.component1().isNotBlank())
        assertEquals(3, policy.component2())
        assertEquals(policy.component3(), policy.copy().audit)
    }

    @Test
    fun `given flow and movement value objects when reading components then persisted shape is explicit`() {
        val metrics = FlowMetrics(throughput = 1, wipCount = 2, blockedCount = 3, avgAgingDays = 4.0)
        val movement = Movement(type = MovementType.MOVED, cardId = "c-1", day = SimulationDay(1), reason = "ok")
        val snapshot =
            DailySnapshot(
                simulationId = "sim-1",
                day = SimulationDay(1),
                metrics = metrics,
                movements = listOf(movement),
            )

        assertEquals(1, metrics.component2())
        assertEquals(2, metrics.component3())
        assertEquals(3, metrics.component4())
        assertEquals(4.0, metrics.component5())

        assertEquals(MovementType.MOVED, movement.component2())
        assertEquals("c-1", movement.component3())
        assertEquals(1, movement.component4().value)
        assertEquals("ok", movement.component5())

        assertEquals("sim-1", snapshot.component2())
        assertEquals(1, snapshot.component3().value)
        assertEquals(metrics, snapshot.component4())
        assertEquals(1, snapshot.component5().size)
    }

    @Test
    fun `given scenario rules and decisions when reading components then command contract is explicit`() {
        val rules = ScenarioRules.create(wipLimit = 4, teamSize = 3, seedValue = 99L)
        val move = Decision.move("card-1")
        val block = Decision.block("card-1", "dependency")
        val unblock = Decision.unblock("card-1")
        val add = Decision.addItem("new", ServiceClass.FIXED_DATE.name)

        assertEquals(4, rules.component2().wipLimit)
        assertEquals(4, rules.component3())
        assertEquals(3, rules.component4())
        assertEquals(99L, rules.component5())

        assertEquals(DecisionType.MOVE_ITEM, move.component2())
        assertEquals("card-1", move.component3()["cardId"])
        assertEquals(DecisionType.BLOCK_ITEM, block.type)
        assertEquals(DecisionType.UNBLOCK_ITEM, unblock.type)
        assertEquals("FIXED_DATE", add.payload["serviceClass"])
    }
}
