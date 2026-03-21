package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardBoardAndValueObjectContractsTest {
    @Test
    fun `given card when consuming effort for each ability then remaining effort is updated per ability`() {
        val card =
            Card(
                stepId = "s-1",
                title = "Feature",
                analysisEffort = 2,
                developmentEffort = 3,
                testEffort = 4,
                deployEffort = 5,
            )

        val afterAnalysis = card.consumeEffort(AbilityName.PRODUCT_MANAGER, 1)
        val afterDevelopment = card.consumeEffort(AbilityName.DEVELOPER, 1)
        val afterTest = card.consumeEffort(AbilityName.TESTER, 2)
        val afterDeploy = card.consumeEffort(AbilityName.DEPLOYER, 3)

        assertEquals(1, afterAnalysis.remainingAnalysisEffort)
        assertEquals(2, afterDevelopment.remainingDevelopmentEffort)
        assertEquals(2, afterTest.remainingTestEffort)
        assertEquals(2, afterDeploy.remainingDeployEffort)
        assertFailsWith<IllegalArgumentException> { card.consumeEffort(AbilityName.DEVELOPER, -1) }
    }

    @Test
    fun `given card data class contract when copying then equality hash and textual identity remain coherent`() {
        val original =
            Card(
                id = "card-1",
                stepId = "step-1",
                title = "Task",
                description = "desc",
                position = 1,
                serviceClass = ServiceClass.FIXED_DATE,
                state = CardState.IN_PROGRESS,
                agingDays = 2,
                analysisEffort = 1,
                developmentEffort = 2,
                testEffort = 3,
                deployEffort = 4,
            )
        val same = original.copy()
        val changed = original.copy(title = "Task 2")

        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
        assertNotEquals(original, changed)
        assertTrue(original.toString().contains("card-1"))
    }

    @Test
    fun `given board with many steps when adding card then only targeted step changes`() {
        val board =
            Board
                .create(name = "Main")
                .addStep("Analysis", AbilityName.PRODUCT_MANAGER)
                .addStep("Development", AbilityName.DEVELOPER)
        val analysis = board.steps.first { it.requiredAbility == AbilityName.PRODUCT_MANAGER }
        val development = board.steps.first { it.requiredAbility == AbilityName.DEVELOPER }

        val updated = board.addCard(stepId = development.id, title = "Build", description = "impl")

        val updatedAnalysis = updated.steps.first { it.id == analysis.id }
        val updatedDevelopment = updated.steps.first { it.id == development.id }
        assertTrue(updatedAnalysis.cards.isEmpty())
        assertEquals(1, updatedDevelopment.cards.size)
        assertEquals("impl", updatedDevelopment.cards.first().description)
    }

    @Test
    fun `given value object data classes when copying then contracts are consistent`() {
        val flow = FlowMetrics(id = "f-1", throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 1.0)
        val movement = Movement(id = "m-1", type = MovementType.MOVED, cardId = "c-1", day = SimulationDay(2), reason = "ok")
        val policy = PolicySet(id = "p-1", wipLimit = 2)
        val decision = Decision(id = "d-1", type = DecisionType.MOVE_ITEM, payload = mapOf("cardId" to "c-1"))
        val snapshot =
            DailySnapshot(
                id = "snap-1",
                simulationId = "sim-1",
                day = SimulationDay(2),
                metrics = flow,
                movements = listOf(movement),
            )

        assertEquals(flow, flow.copy())
        assertEquals(movement, movement.copy())
        assertEquals(policy, policy.copy())
        assertEquals(decision, decision.copy())
        assertEquals(snapshot, snapshot.copy())
        assertNotEquals(policy, policy.copy(wipLimit = 3))
    }
}
