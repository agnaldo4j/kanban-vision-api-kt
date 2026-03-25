package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StepValidationAndExecutionBehaviorTest {
    @Test
    fun `given invalid step constructor and factory inputs when creating then invariants reject invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            Step(id = "", board = BoardRef("b-1"), name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", board = BoardRef(""), name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", board = BoardRef("b-1"), name = "", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "s-1", board = BoardRef("b-1"), name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = BoardRef(""), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = BoardRef("b-1"), name = "", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = BoardRef("b-1"), name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
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
                board = BoardRef("b-1"),
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
                .create(board = BoardRef("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
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
                .create(board = BoardRef("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(developer)
        val card = Card(step = StepRef(step.id), title = "Task", state = CardState.IN_PROGRESS, developmentEffort = 1)

        assertFailsWith<IllegalArgumentException> {
            step.executeCard(worker = tester, card = card, dailyCapacities = mapOf(AbilityName.DEVELOPER to 1))
        }
    }

    @Test
    fun `given worker already assigned to step when assigning again then operation is rejected`() {
        val worker =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val step =
            Step
                .create(board = BoardRef("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(worker)

        assertFailsWith<IllegalArgumentException> { step.assignWorker(worker) }
    }

    @Test
    fun `given empty daily capacities map when executing card then zero effort is consumed`() {
        val worker =
            Worker(
                name = "Dev",
                abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
            )
        val step =
            Step
                .create(board = BoardRef("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(worker)
        val card = Card(step = StepRef(step.id), title = "Task", state = CardState.IN_PROGRESS, developmentEffort = 3)

        val result = step.executeCard(worker = worker, card = card, dailyCapacities = emptyMap())

        assertEquals(0, result.consumedEffort)
        assertEquals(false, result.isStepCompleted)
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
                .create(board = BoardRef("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(worker)
        val doneEffortCard =
            Card(
                step = StepRef(step.id),
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
