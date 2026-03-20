package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StepAndBoardBehaviorTest {
    @Test
    fun `given board workflow when adding duplicated step or unknown target then operation fails`() {
        val board = Board.create("Main").addStep("Analysis", AbilityName.PRODUCT_MANAGER)

        assertFailsWith<IllegalArgumentException> { board.addStep("Analysis", AbilityName.PRODUCT_MANAGER) }
        assertFailsWith<IllegalStateException> { board.addCard("missing", "Card") }
    }

    @Test
    fun `given step worker assignment lifecycle when assigning twice then duplicate assignment is rejected`() {
        val step = Step.create("board-1", "Dev", 0, AbilityName.DEVELOPER)
        val worker = devWorker(id = "w-1")

        val assigned = step.assignWorker(worker)
        assertTrue(assigned.canAssign(worker))
        assertEquals(1, assigned.workers.size)

        assertFailsWith<IllegalArgumentException> { assigned.assignWorker(worker) }
        val unassigned = assigned.unassignWorker("w-1")
        assertTrue(unassigned.workers.isEmpty())
    }

    @Test
    fun `given deploy step when executing card then consumed effort marks step completion`() {
        val deployStep = Step.create("board-1", "Deploy", 1, AbilityName.DEPLOYER)
        val deployWorker = deployerWorker("w-deploy")
        val inProgress = Card(stepId = deployStep.id, title = "Release", state = CardState.IN_PROGRESS, deployEffort = 3)

        val deployResult = deployStep.executeCard(deployWorker, inProgress, dailyCapacities = mapOf(AbilityName.DEPLOYER to 0))
        assertEquals(3, deployResult.consumedEffort)
        assertEquals(0, deployResult.updatedCard.remainingDeployEffort)
        assertTrue(deployResult.isStepCompleted)

        val doneCard = inProgress.copy(remainingDeployEffort = 0)
        val doneResult = deployStep.executeCard(deployWorker, doneCard, emptyMap())
        assertEquals(0, doneResult.consumedEffort)
        assertTrue(doneResult.isStepCompleted)
    }

    @Test
    fun `given worker without required ability when executing step then execution is rejected`() {
        val analysisStep = Step.create("board-1", "Analysis", 0, AbilityName.PRODUCT_MANAGER)
        val dev = devWorker(id = "w-dev")
        val card = Card(stepId = analysisStep.id, title = "Spec", state = CardState.IN_PROGRESS, analysisEffort = 1)

        assertFailsWith<IllegalArgumentException> {
            analysisStep.executeCard(dev, card, dailyCapacities = mapOf(AbilityName.PRODUCT_MANAGER to 1))
        }
    }

    private fun devWorker(id: String): Worker =
        Worker(
            id = id,
            name = "Dev",
            abilities =
                setOf(
                    Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                ),
        )

    private fun deployerWorker(id: String): Worker =
        Worker(
            id = id,
            name = "Ops",
            abilities = setOf(Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR)),
        )
}
