package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.Worker
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Rodada de kill do GAP-AT: os survivors do PITest se concentravam em guards
 * `require(...)` cujo caminho de VIOLAÇÃO nunca era exercitado — cada teste
 * abaixo mata mutantes RemoveConditional/ConditionalsBoundary específicos
 * de Card/Step/Worker/Board (linhas citadas no relatório de 2026-07-06).
 */
class KanbanGuardViolationBehaviorTest {
    private val devAbility = Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)
    private val devWorker = Worker(name = "Dev", abilities = setOf(devAbility))

    @Test
    fun `given negative efforts when constructing card then each guard rejects`() {
        assertFailsWith<IllegalArgumentException> { card(analysisEffort = -1) }
        assertFailsWith<IllegalArgumentException> { card(developmentEffort = -1) }
        assertFailsWith<IllegalArgumentException> { card(testEffort = -1) }
        assertFailsWith<IllegalArgumentException> { card(deployEffort = -1) }
    }

    @Test
    fun `given blank title when creating card via factory then guard rejects`() {
        assertFailsWith<IllegalArgumentException> {
            Card.create(step = StepRef("s1"), title = "   ", description = "", position = 0)
        }
    }

    @Test
    fun `given negative target position when moving card then guard rejects and zero is accepted`() {
        val c = card()
        assertFailsWith<IllegalArgumentException> { c.moveTo(StepRef("s2"), newPosition = -1) }
        assertEquals(0, c.moveTo(StepRef("s2"), newPosition = 0).position)
    }

    @Test
    fun `given negative points when consuming effort then guard rejects and zero consumes nothing`() {
        // remaining < effort de propósito: com o guard mutado, remaining-(-1) ainda
        // caberia no range do init — só assim o require de points é o ÚNICO a falhar.
        val c =
            card(developmentEffort = 3)
                .consumeEffort(AbilityName.DEVELOPER, points = 2, now = Instant.EPOCH)
        assertEquals(1, c.remainingEffortFor(AbilityName.DEVELOPER))
        assertFailsWith<IllegalArgumentException> {
            c.consumeEffort(AbilityName.DEVELOPER, points = -1, now = Instant.EPOCH)
        }
        val untouched = c.consumeEffort(AbilityName.DEVELOPER, points = 0, now = Instant.EPOCH)
        assertEquals(1, untouched.remainingEffortFor(AbilityName.DEVELOPER))
    }

    @Test
    fun `given invalid capacity bounds when generating daily capacities then guards reject`() {
        assertFailsWith<IllegalArgumentException> {
            devWorker.generateDailyCapacities(Random(1), minPoints = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            devWorker.generateDailyCapacities(Random(1), minPoints = 5, maxPoints = 4)
        }
    }

    @Test
    fun `given ability profile when generating daily capacities then each branch yields its capacity`() {
        val deployer =
            Worker(
                name = "Ops",
                abilities =
                    setOf(
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR),
                        devAbility,
                    ),
            )
        val capacities = deployer.generateDailyCapacities(Random(42), minPoints = 3, maxPoints = 3)
        assertEquals(0, capacities[AbilityName.TESTER]) // sem a ability → 0
        assertEquals(Int.MAX_VALUE, capacities[AbilityName.DEPLOYER]) // deployer → ilimitado
        assertEquals(3, capacities[AbilityName.DEVELOPER]) // min==max → determinístico
    }

    @Test
    fun `given invalid step creation inputs then each guard rejects`() {
        val board = Board.create("B")
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = board.toRef(), name = " ", position = 0, requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.create(board = board.toRef(), name = "Dev", position = -1, requiredAbility = AbilityName.DEVELOPER)
        }
    }

    @Test
    fun `given wrong ability or duplicate when assigning worker then guards reject`() {
        val step =
            Board
                .create("B")
                .addStep(name = "Test", requiredAbility = AbilityName.TESTER)
                .steps
                .first()
        assertFailsWith<IllegalArgumentException> { step.assignWorker(devWorker) }

        val devStep =
            Board
                .create("B")
                .addStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
                .steps
                .first()
        val assigned = devStep.assignWorker(devWorker)
        assertFailsWith<IllegalArgumentException> { assigned.assignWorker(devWorker) }
    }

    @Test
    fun `given card without remaining effort when executing then step reports completion without consumption`() {
        val step =
            Board
                .create("B")
                .addStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
                .steps
                .first()
        val done = card(developmentEffort = 0)
        val result = step.executeCard(devWorker, done, dailyCapacities = emptyMap(), now = Instant.EPOCH)
        assertEquals(0, result.consumedEffort)
        assertTrue(result.isStepCompleted)
    }

    @Test
    fun `given missing capacity when executing card then nothing is consumed`() {
        val step =
            Board
                .create("B")
                .addStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
                .steps
                .first()
        val pending = card(developmentEffort = 5)
        val result = step.executeCard(devWorker, pending, dailyCapacities = emptyMap(), now = Instant.EPOCH)
        assertEquals(0, result.consumedEffort)
        assertEquals(5, result.updatedCard.remainingEffortFor(AbilityName.DEVELOPER))
    }

    @Test
    fun `given invalid board inputs then create addStep and addCard guards reject`() {
        assertFailsWith<IllegalArgumentException> { Board.create("  ") }

        val board = Board.create("B").addStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        assertFailsWith<IllegalArgumentException> { board.addStep(name = " ", requiredAbility = AbilityName.TESTER) }
        assertFailsWith<IllegalArgumentException> { board.addStep(name = "Dev", requiredAbility = AbilityName.TESTER) }
        assertFailsWith<IllegalStateException> { board.addCard(step = StepRef("nope"), title = "T") }
    }

    private fun card(
        analysisEffort: Int = 0,
        developmentEffort: Int = 0,
        testEffort: Int = 0,
        deployEffort: Int = 0,
    ): Card =
        Card(
            id = "c1",
            step = StepRef("s1"),
            title = "T",
            analysisEffort = analysisEffort,
            developmentEffort = developmentEffort,
            testEffort = testEffort,
            deployEffort = deployEffort,
            remainingAnalysisEffort = analysisEffort,
            remainingDevelopmentEffort = developmentEffort,
            remainingTestEffort = testEffort,
            remainingDeployEffort = deployEffort,
        )
}
