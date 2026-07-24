package com.kanbanvision.domain.model

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.KanbanError
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoardStepCardExecutionBehaviorTest {
    @Test
    fun `given board when adding steps then names must be unique`() {
        val board = Board.create(name = "Flow Board")
        val boardWithStep = board.withStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)

        // ADR-0044: nome de step duplicado é regra de domínio → Left(DuplicateStepName), não exceção.
        val error = boardWithStep.addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER).leftOrNull()

        assertIs<KanbanError.DuplicateStepName>(error)
    }

    @Test
    fun `given board and step when adding card then card is appended to target step`() {
        val board = Board.create(name = "Flow").withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val stepId = board.steps.first().id

        val updated = board.withCard(step = stepId, title = "Build API", description = "Implement endpoint")

        val step = updated.steps.first()
        val card = step.cards.first()
        assertEquals(1, step.cards.size)
        assertEquals("Build API", card.title)
        assertEquals(stepId, card.step)
    }

    @Test
    fun `given in progress card and developer step when executing then consumed effort follows daily capacity`() {
        val dev = worker("Dev")
        val step =
            Step
                .create(board = BoardId("board-1"), name = "Development", position = 1, requiredAbility = AbilityName.DEVELOPER)
                .assignWorker(dev)
        val card =
            Card(
                step = step.id,
                title = "Feature",
                state = CardState.IN_PROGRESS,
                developmentEffort = 5,
                remainingDevelopmentEffort = 5,
            )

        val result =
            step.executeCard(worker = dev, card = card, dailyCapacities = dev.generateDailyCapacities(Random(1), 2, 2), now = Instant.EPOCH)

        assertEquals(2, result.consumedEffort)
        assertEquals(3, result.updatedCard.remainingDevelopmentEffort)
        assertTrue(!result.isStepCompleted)
    }

    @Test
    fun `given deploy step when executing then remaining deploy effort is fully consumed regardless of daily capacity`() {
        val deployer = deployWorker()
        val step =
            Step
                .create(board = BoardId("board-1"), name = "Deploy", position = 3, requiredAbility = AbilityName.DEPLOYER)
                .assignWorker(deployer)
        val card =
            Card(
                step = step.id,
                title = "Release",
                state = CardState.IN_PROGRESS,
                deployEffort = 4,
                remainingDeployEffort = 4,
            )

        val result =
            step.executeCard(worker = deployer, card = card, dailyCapacities = mapOf(AbilityName.DEPLOYER to 0), now = Instant.EPOCH)

        assertEquals(4, result.consumedEffort)
        assertEquals(0, result.updatedCard.remainingDeployEffort)
        assertTrue(result.isStepCompleted)
    }

    @Test
    fun `given card effort consumption when consuming points then audit timestamp is touched`() {
        val baseAudit = Audit.now(Instant.parse("2026-03-20T00:00:00Z"))
        val card =
            Card(
                step = StepId("step-1"),
                title = "Spec",
                analysisEffort = 3,
                remainingAnalysisEffort = 3,
                audit = baseAudit,
            )

        val updated = card.consumeEffort(AbilityName.PRODUCT_MANAGER, points = 1, now = Instant.parse("2026-03-21T00:00:00Z"))

        assertEquals(2, updated.remainingAnalysisEffort)
        assertEquals(baseAudit.createdAt, updated.audit.createdAt)
        assertEquals(Instant.parse("2026-03-21T00:00:00Z"), updated.audit.updatedAt)
    }

    @Test
    fun `given non in progress card when blocking then operation is rejected`() {
        val todo = Card(step = StepId("step-1"), title = "Task", state = CardState.TODO)

        assertFailsWith<IllegalArgumentException> {
            todo.block()
        }
    }

    private fun worker(name: String): Worker =
        Worker(
            name = name,
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
        )

    private fun deployWorker(): Worker =
        Worker(
            name = "Deploy",
            abilities =
                setOf(
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR),
                    Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                ),
        )
}
