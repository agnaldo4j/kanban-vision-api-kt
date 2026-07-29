package com.kanbanvision.domain.model

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.common.model.NonBlankTitle
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
        assertEquals("Build API", card.title.value)
        assertEquals(stepId, card.step)
    }

    @Test
    fun `given in progress card and developer step when executing then consumed effort follows daily capacity`() {
        val dev = worker("Dev")
        val step =
            Step
                .create(board = BoardId("board-1"), name = "Development", position = 1, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(dev)
        val card =
            Card(
                step = step.id,
                title = NonBlankTitle("Feature"),
                state = CardState.IN_PROGRESS,
                developmentEffort = 5,
                remainingDevelopmentEffort = 5,
            )

        val result =
            step.execute(worker = dev, card = card, dailyCapacities = dev.generateDailyCapacities(Random(1), 2, 2), now = Instant.EPOCH)

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
                .withWorker(deployer)
        val card =
            Card(
                step = step.id,
                title = NonBlankTitle("Release"),
                state = CardState.IN_PROGRESS,
                deployEffort = 4,
                remainingDeployEffort = 4,
            )

        val result =
            step.execute(worker = deployer, card = card, dailyCapacities = mapOf(AbilityName.DEPLOYER to 0), now = Instant.EPOCH)

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
                title = NonBlankTitle("Spec"),
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
        val todo = Card(step = StepId("step-1"), title = NonBlankTitle("Task"), state = CardState.TODO)

        assertIs<KanbanError.CardNotInProgress>(todo.block().leftOrNull())
    }

    @Test
    fun `given steps stored out of order when asking for execution order then they come sorted by position`() {
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
                .withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
                .withStep(name = "Deploy", requiredAbility = AbilityName.DEPLOYER)
        // Um decode que preserva a ordem do array JSON pode devolver os steps em qualquer ordem.
        val scrambled = board.copy(steps = board.steps.reversed())

        val ordered = scrambled.stepsInExecutionOrder()

        assertEquals(listOf("Analysis", "Development", "Deploy"), ordered.map { it.name.value })
        assertEquals(listOf(0, 1, 2), ordered.map { it.position })
    }

    @Test
    fun `given steps sharing a position when asking for execution order then insertion order is kept`() {
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "First", requiredAbility = AbilityName.DEVELOPER)
                .withStep(name = "Second", requiredAbility = AbilityName.TESTER)
        // `sortedBy` é estável: empate em `position` não embaralha, o que mantém o runDay determinístico.
        val tied = board.copy(steps = board.steps.map { it.copy(position = 0) })

        assertEquals(listOf("First", "Second"), tied.stepsInExecutionOrder().map { it.name.value })
    }

    @Test
    fun `given board without steps when counting items then result is zero`() {
        assertEquals(0, Board.create(name = "Empty").itemCount())
    }

    @Test
    fun `given steps without cards when counting items then result is zero`() {
        // Distingue "sem steps" de "steps vazios": é este caso que mata o mutante que troca a soma
        // por uma constante ou ignora os steps sem card.
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
                .withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)

        assertEquals(0, board.itemCount())
    }

    @Test
    fun `given cards spread across steps when counting items then all of them are summed`() {
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
                .withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val analysis = board.steps[0].id
        val development = board.steps[1].id
        val filled =
            board
                .withCard(step = analysis, title = "A1")
                .withCard(step = analysis, title = "A2")
                .withCard(step = development, title = "D1")

        // Soma ATRAVÉS dos steps — um `first()`/`maxOf` no lugar do `sumOf` daria 2 e passaria despercebido
        // se todos os cards estivessem num step só.
        assertEquals(3, filled.itemCount())
    }

    @Test
    fun `given board when asking for the first step then it is the head of the execution order`() {
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
                .withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val scrambled = board.copy(steps = board.steps.reversed())

        assertEquals("Analysis", scrambled.firstStep()?.name?.value)
    }

    @Test
    fun `given steps sharing a position when asking for the first step then insertion order decides`() {
        // Equivalência que o call site do engine dependia implicitamente: `minByOrNull { position }`
        // devolve o PRIMEIRO mínimo na ordem de iteração, e `sortedBy` é estável — então o head da ordem
        // de execução é o mesmo elemento. Sem este caso a substituição seria só plausível, não provada.
        val board =
            Board
                .create(name = "Flow")
                .withStep(name = "First", requiredAbility = AbilityName.DEVELOPER)
                .withStep(name = "Second", requiredAbility = AbilityName.TESTER)
        val tied = board.copy(steps = board.steps.map { it.copy(position = 0) })

        assertEquals("First", tied.firstStep()?.name?.value)
        assertEquals(tied.steps.minByOrNull { it.position }, tied.firstStep())
    }

    @Test
    fun `given board without steps when asking for the first step then result is null`() {
        assertEquals(null, Board.create(name = "Empty").firstStep())
    }

    private fun worker(name: String): Worker =
        Worker(
            name = NonBlankName(name),
            abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
        )

    private fun deployWorker(): Worker =
        Worker(
            name = NonBlankName("Deploy"),
            abilities =
                setOf(
                    Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR),
                    Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                ),
        )
}
