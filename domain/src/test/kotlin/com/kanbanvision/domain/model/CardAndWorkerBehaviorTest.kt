package com.kanbanvision.domain.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardAndWorkerBehaviorTest {
    @Test
    fun `given card states when moving blocking and advancing then transitions are correct`() {
        val card = Card(stepId = "s-1", title = "Card", state = CardState.BLOCKED, developmentEffort = 1)
        val doneCard = card.advance().advance().advance()

        assertEquals(CardState.IN_PROGRESS, card.advance().state)
        assertEquals(CardState.DONE, card.advance().advance().state)
        assertEquals(CardState.DONE, doneCard.state)
        assertEquals("s-2", card.moveTo("s-2", 3).stepId)
        assertEquals(3, card.moveTo("s-2", 3).position)

        assertFailsWith<IllegalArgumentException> { card.block() }
        assertFailsWith<IllegalArgumentException> { card.moveTo("", 0) }
        assertFailsWith<IllegalArgumentException> { card.moveTo("s-2", -1) }
    }

    @Test
    fun `given invalid effort values when creating or consuming then validation fails`() {
        assertFailsWith<IllegalArgumentException> {
            Card(stepId = "s", title = "x", analysisEffort = 1, remainingAnalysisEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(stepId = "s", title = "x", developmentEffort = 1, remainingDevelopmentEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(stepId = "s", title = "x", testEffort = 1, remainingTestEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(stepId = "s", title = "x", deployEffort = 1, remainingDeployEffort = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            Card(stepId = "s", title = "x").consumeEffort(AbilityName.TESTER, -1)
        }
    }

    @Test
    fun `given worker abilities when generating capacities then constraints are respected`() {
        val worker =
            Worker(
                name = "Dev",
                abilities =
                    setOf(
                        Ability(name = AbilityName.DEVELOPER, seniority = Seniority.JR),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.JR),
                    ),
            )
        val step = Step.create("b-1", "Dev", 0, AbilityName.DEVELOPER)
        val caps = worker.generateDailyCapacities(Random(7), minPoints = 1, maxPoints = 1)

        assertTrue(worker.canExecute(step))
        assertEquals(1, caps[AbilityName.DEVELOPER])
        assertEquals(Int.MAX_VALUE, caps[AbilityName.DEPLOYER])

        assertFailsWith<IllegalArgumentException> { worker.generateDailyCapacities(Random(1), minPoints = -1, maxPoints = 2) }
        assertFailsWith<IllegalArgumentException> { worker.generateDailyCapacities(Random(1), minPoints = 3, maxPoints = 2) }
    }
}
