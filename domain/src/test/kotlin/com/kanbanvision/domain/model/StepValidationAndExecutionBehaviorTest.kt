package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StepValidationAndExecutionBehaviorTest {
    @Test
    fun `given invalid step constructor and factory inputs when creating then invariants reject invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            Step(id = "", boardId = "b-1", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", boardId = "", name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", boardId = "b-1", name = "", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", boardId = "b-1", name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(boardId = "", name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(boardId = "b-1", name = "", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(boardId = "b-1", name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }
    }

    @Test
    fun `given step workers list with incompatible ability when constructing then step rejects invalid assignment`() {
        val incompatible =
            Worker(
                name = "PM",
                abilities = setOf(Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.JR)),
            )

        assertFailsWith<IllegalArgumentException> {
            Step(
                boardId = "b-1",
                name = "Dev",
                requiredAbility = AbilityName.DEVELOPER,
                workers = listOf(incompatible),
            )
        }
    }

    @Test
    fun `given assigned worker when unassigning then step no longer keeps worker`() {
        val worker =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val step =
            Step
                .create(boardId = "b-1", name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(worker)

        val updated = step.unassignWorker(worker.id)

        assertTrue(updated.workers.isEmpty())
    }

    @Test
    fun `given incompatible worker executing step when invoking execution then operation is rejected`() {
        val developer =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val tester =
            Worker(
                name = "Tester",
                abilities =
                    setOf(
                        Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                    ),
            )
        val step =
            Step
                .create(boardId = "b-1", name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(developer)
        val card = Card(stepId = step.id, title = "Task", state = CardState.IN_PROGRESS, developmentEffort = 1)

        assertFailsWith<IllegalArgumentException> {
            step.executeCard(worker = tester, card = card, dailyCapacities = mapOf(AbilityName.DEVELOPER to 1))
        }
    }

    @Test
    fun `given card already completed for step ability when executing then execution consumes zero and reports completed`() {
        val worker =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val step =
            Step
                .create(boardId = "b-1", name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(worker)
        val doneEffortCard =
            Card(
                stepId = step.id,
                title = "Task",
                state = CardState.IN_PROGRESS,
                developmentEffort = 3,
                remainingDevelopmentEffort = 0,
            )

        val result = step.executeCard(worker = worker, card = doneEffortCard, dailyCapacities = mapOf(AbilityName.DEVELOPER to 2))

        assertEquals(0, result.consumedEffort)
        assertTrue(result.isStepCompleted)
    }
}
