package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Worker
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardWorkerBehaviorPropertyTest {
    @Test
    fun `Card create rejects any blank title`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { Card.create(step = STEP_REF, title = blank, position = 0) }.isFailure
            }
        }
    }

    @Test
    fun `Card create rejects any negative position`() {
        runBlocking {
            forAll(Arb.int(NEG_BOUND..-1)) { neg ->
                runCatching { Card.create(step = STEP_REF, title = "T", position = neg) }.isFailure
            }
        }
    }

    @Test
    fun `Card advance from TODO always yields IN_PROGRESS`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { title ->
                Card
                    .create(step = STEP_REF, title = title, position = 0)
                    .advance()
                    .state == CardState.IN_PROGRESS
            }
        }
    }

    @Test
    fun `Card advance from IN_PROGRESS always yields DONE`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { title ->
                Card
                    .create(step = STEP_REF, title = title, position = 0)
                    .advance()
                    .advance()
                    .state == CardState.DONE
            }
        }
    }

    @Test
    fun `Card advance from DONE is idempotent`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { title ->
                val done = Card.create(step = STEP_REF, title = title, position = 0).advance().advance()
                done.advance() == done
            }
        }
    }

    @Test
    fun `Card incrementAge always increases agingDays by exactly one`() {
        runBlocking {
            checkAll(ARB_NON_BLANK) { title ->
                val card = Card.create(step = STEP_REF, title = title, position = 0)
                assertEquals(1, card.incrementAge().agingDays)
            }
        }
    }

    @Test
    fun `Card consumeEffort never produces negative remaining effort for any ability and points`() {
        runBlocking {
            checkAll(
                Arb.int(1..MAX_EFFORT),
                Arb.int(0..MAX_CONSUMED),
                Arb.of(AbilityName.PRODUCT_MANAGER, AbilityName.DEVELOPER, AbilityName.TESTER, AbilityName.DEPLOYER),
            ) { effort, points, ability ->
                val card =
                    Card(
                        id = CardId("card-1"),
                        step = STEP_REF,
                        title = "T",
                        analysisEffort = effort,
                        remainingAnalysisEffort = effort,
                        developmentEffort = effort,
                        remainingDevelopmentEffort = effort,
                        testEffort = effort,
                        remainingTestEffort = effort,
                        deployEffort = effort,
                        remainingDeployEffort = effort,
                    )
                assertTrue(card.consumeEffort(ability, points, Instant.EPOCH).remainingEffortFor(ability) >= 0)
            }
        }
    }

    @Test
    fun `Worker rejects empty ability set`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Worker(name = name, abilities = emptySet()) }.isFailure
            }
        }
    }

    @Test
    fun `Worker with TESTER always requires DEPLOYER`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                val testerOnly = setOf(Ability(name = AbilityName.TESTER, seniority = Seniority.PL))
                runCatching { Worker(name = name, abilities = testerOnly) }.isFailure
            }
        }
    }

    @Test
    fun `Worker generateDailyCapacities always assigns MAX_VALUE to DEPLOYER`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                val worker = Worker(name = name, abilities = DEPLOYER_ABILITIES)
                worker.generateDailyCapacities(Random.Default)[AbilityName.DEPLOYER] == Int.MAX_VALUE
            }
        }
    }

    @Test
    fun `Worker generateDailyCapacities returns zero for abilities not held`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                val worker = Worker(name = name, abilities = DEV_ONLY_ABILITIES)
                val caps = worker.generateDailyCapacities(Random.Default)
                caps[AbilityName.PRODUCT_MANAGER] == 0 &&
                    caps[AbilityName.TESTER] == 0 &&
                    caps[AbilityName.DEPLOYER] == 0
            }
        }
    }

    private companion object {
        const val NAME_MAX = 50
        const val NEG_BOUND = -1000
        const val MAX_EFFORT = 20
        const val MAX_CONSUMED = 50
        val STEP_REF = StepId("step-1")
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = NAME_MAX).filter { it.isNotBlank() }
        val DEPLOYER_ABILITIES =
            setOf(
                Ability(name = AbilityName.TESTER, seniority = Seniority.PL),
                Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
            )
        val DEV_ONLY_ABILITIES = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL))
    }
}
