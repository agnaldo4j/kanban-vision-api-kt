package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.Worker
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class StepWorkerCapacityPropertyTest {
    @Test
    fun `Step create rejects any blank name`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching {
                    Step.create(board = BOARD_REF, name = blank, position = 0, requiredAbility = AbilityName.DEVELOPER)
                }.isFailure
            }
        }
    }

    @Test
    fun `Step create rejects any negative position`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..-1)) { neg ->
                runCatching {
                    Step.create(board = BOARD_REF, name = "Dev", position = neg, requiredAbility = AbilityName.DEVELOPER)
                }.isFailure
            }
        }
    }

    @Test
    fun `Step assignWorker rejects worker without required ability`() {
        runBlocking {
            forAll(
                Arb.of(AbilityName.PRODUCT_MANAGER, AbilityName.DEVELOPER, AbilityName.TESTER),
                ARB_NON_BLANK,
            ) { stepAbility, workerName ->
                val differentAbility = AbilityName.entries.first { it != stepAbility && it != AbilityName.TESTER }
                val step = Step.create(board = BOARD_REF, name = "Step", position = 0, requiredAbility = stepAbility)
                val worker =
                    Worker(
                        name = workerName,
                        abilities = setOf(Ability(name = differentAbility, seniority = Seniority.PL)),
                    )
                runCatching { step.withWorker(worker) }.isFailure
            }
        }
    }

    @Test
    fun `Step assignWorker rejects duplicate worker assignment`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                val worker =
                    Worker(
                        name = name,
                        abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)),
                    )
                val step =
                    Step
                        .create(board = BOARD_REF, name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                        .withWorker(worker)
                runCatching { step.withWorker(worker) }.isFailure
            }
        }
    }

    @Test
    fun `Step assignWorker accepts worker with matching ability`() {
        runBlocking {
            forAll(
                Arb.of(AbilityName.PRODUCT_MANAGER, AbilityName.DEVELOPER, AbilityName.DEPLOYER),
                ARB_NON_BLANK,
            ) { ability, name ->
                val worker = Worker(name = name, abilities = setOf(Ability(name = ability, seniority = Seniority.PL)))
                val step = Step.create(board = BOARD_REF, name = "Step", position = 0, requiredAbility = ability)
                runCatching { step.withWorker(worker) }.isSuccess
            }
        }
    }

    @Test
    fun `Worker generateDailyCapacities returns zero for any ability not held`() {
        runBlocking {
            forAll(
                ARB_NON_BLANK,
                Arb.of(AbilityName.PRODUCT_MANAGER, AbilityName.TESTER),
            ) { name, absentAbility ->
                val worker =
                    Worker(name = name, abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
                worker.generateDailyCapacities(Random.Default)[absentAbility] == 0
            }
        }
    }

    @Test
    fun `Worker generateDailyCapacities result for held non-DEPLOYER ability is always within minPoints and maxPoints`() {
        runBlocking {
            checkAll(
                ARB_NON_BLANK,
                Arb.int(0..MAX_POINTS),
                Arb.int(0..MAX_POINTS),
            ) { name, min, extra ->
                val max = min + extra
                val worker =
                    Worker(name = name, abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
                val capacity = worker.generateDailyCapacities(Random.Default, minPoints = min, maxPoints = max)[AbilityName.DEVELOPER] ?: 0
                assertTrue(capacity in min..max)
            }
        }
    }

    @Test
    fun `Worker generateDailyCapacities rejects negative minPoints`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                val worker =
                    Worker(name = name, abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
                runCatching { worker.generateDailyCapacities(Random.Default, minPoints = -1) }.isFailure
            }
        }
    }

    @Test
    fun `Worker generateDailyCapacities rejects maxPoints less than minPoints`() {
        runBlocking {
            checkAll(
                ARB_NON_BLANK,
                Arb.int(1..MAX_POINTS),
            ) { name, min ->
                val worker =
                    Worker(name = name, abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
                runCatching { worker.generateDailyCapacities(Random.Default, minPoints = min, maxPoints = min - 1) }.isFailure
            }
        }
    }

    private companion object {
        const val NAME_MAX = 50
        const val NEG_BOUND = -1000
        const val MAX_POINTS = 50
        val BOARD_REF = BoardId("board-1")
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = NAME_MAX).filter { it.isNotBlank() }
    }
}
