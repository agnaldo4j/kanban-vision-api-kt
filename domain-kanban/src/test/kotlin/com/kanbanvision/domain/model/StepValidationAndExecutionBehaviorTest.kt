package com.kanbanvision.domain.model
import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.KanbanError
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StepValidationAndExecutionBehaviorTest {
    @Test
    fun `given invalid step constructor and factory inputs when creating then invariants reject invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            Step(id = StepId(""), board = BoardId("b-1"), name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = StepId("s-1"), board = BoardId(""), name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = StepId("s-1"), board = BoardId("b-1"), name = "", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = StepId("s-1"), board = BoardId("b-1"), name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = BoardId(""), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = BoardId("b-1"), name = "", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = BoardId("b-1"), name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
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
                board = BoardId("b-1"),
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
                .create(board = BoardId("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(worker)

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
                .create(board = BoardId("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(developer)
        val card = Card(step = step.id, title = "Task", state = CardState.IN_PROGRESS, developmentEffort = 1)

        val error =
            step
                .executeCard(
                    worker = tester,
                    card = card,
                    dailyCapacities = mapOf(AbilityName.DEVELOPER to 1),
                    now = Instant.EPOCH,
                ).leftOrNull()
        assertIs<KanbanError.WorkerCannotExecuteStep>(error)
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
                .create(board = BoardId("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(worker)

        assertIs<KanbanError.WorkerAlreadyAssigned>(step.assignWorker(worker).leftOrNull())
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
                .create(board = BoardId("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(worker)
        val card = Card(step = step.id, title = "Task", state = CardState.IN_PROGRESS, developmentEffort = 3)

        val result = step.execute(worker = worker, card = card, dailyCapacities = emptyMap(), now = Instant.EPOCH)

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
                .create(board = BoardId("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(worker)
        val doneEffortCard =
            Card(
                step = step.id,
                title = "Task",
                state = CardState.IN_PROGRESS,
                developmentEffort = 3,
                remainingDevelopmentEffort = 0,
            )

        val result =
            step.execute(
                worker = worker,
                card = doneEffortCard,
                dailyCapacities = mapOf(AbilityName.DEVELOPER to 2),
                now = Instant.EPOCH,
            )

        assertEquals(0, result.consumedEffort)
        assertTrue(result.isStepCompleted)
    }
}
