package com.kanbanvision.domain.model

import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DomainModelBehaviorTest {
    @Test
    fun `given audit when touch is called then updatedAt changes and createdAt remains stable`() {
        val base = Audit(createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        val touched = base.touch(Instant.parse("2026-01-02T00:00:00Z"))

        assertEquals(base.createdAt, touched.createdAt)
        assertNotEquals(base.updatedAt, touched.updatedAt)
    }

    @Test
    fun `given tester ability without deployer when creating worker then invariant is rejected`() {
        val tester = Ability(name = AbilityName.TESTER, seniority = Seniority.PL)
        val deployer = Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL)

        assertFailsWith<IllegalArgumentException> {
            Worker(name = "Only tester", abilities = setOf(tester))
        }

        val worker = Worker(name = "QA", abilities = setOf(tester, deployer))
        assertTrue(worker.hasAbility(AbilityName.DEPLOYER))
        assertNotNull(worker.audit.createdAt)
    }

    @Test
    fun `given card lifecycle when consuming effort and transitioning state then progression is consistent`() {
        val card =
            Card(
                stepId = "step-1",
                title = "Card",
                analysisEffort = 3,
                developmentEffort = 5,
                testEffort = 2,
                deployEffort = 1,
            )

        val analysisDone = card.consumeEffort(AbilityName.PRODUCT_MANAGER, 3)
        val devPartial = analysisDone.consumeEffort(AbilityName.DEVELOPER, 2)

        assertEquals(0, analysisDone.remainingAnalysisEffort)
        assertEquals(3, devPartial.remainingDevelopmentEffort)
        assertEquals(CardState.IN_PROGRESS, card.advance().state)
        assertEquals(CardState.DONE, card.advance().advance().state)
        assertEquals(CardState.BLOCKED, card.advance().block().state)
        assertEquals(2, card.incrementAge().incrementAge().agingDays)
    }

    @Test
    fun `given step required ability when assigning and executing then only compatible workers can run`() {
        val worker = developerWorker()
        val step = developmentStep()
        val card = Card(stepId = step.id, title = "C", developmentEffort = 3)

        val assigned = step.assignWorker(worker)
        val executed = assigned.executeCard(worker, card.advance(), mapOf(AbilityName.DEVELOPER to 2))

        assertEquals(1, assigned.workers.size)
        assertEquals(2, executed.consumedEffort)
        assertEquals(1, executed.updatedCard.remainingDevelopmentEffort)

        assertFailsWith<IllegalArgumentException> {
            val testerOnly =
                Worker(
                    name = "Tester",
                    abilities =
                        setOf(
                            Ability(name = AbilityName.TESTER, seniority = Seniority.JR),
                            Ability(name = AbilityName.DEPLOYER, seniority = Seniority.JR),
                        ),
                )
            step.assignWorker(testerOnly)
        }
    }

    @Test
    fun `given board flow when adding steps and cards then references remain consistent`() {
        val board =
            Board
                .create("Main")
                .addStep("Analysis", AbilityName.PRODUCT_MANAGER)
                .addStep("Dev", AbilityName.DEVELOPER)
        val analysisStep = board.steps.first()
        val updatedBoard = board.addCard(analysisStep.id, "Analyze")
        val card =
            updatedBoard.steps
                .first()
                .cards
                .first()

        assertEquals(analysisStep.id, card.stepId)

        val assignedStep = analysisStep.assignWorker(productManagerWorker())

        assertEquals(1, assignedStep.workers.size)
        assertTrue(assignedStep.workers.first().hasAbility(AbilityName.PRODUCT_MANAGER))
    }

    @Test
    fun `given scenario and simulation aggregates when appending history then temporal state remains consistent`() {
        val policy = PolicySet(wipLimit = 2)
        val rules = ScenarioRules(policySet = policy, wipLimit = 2, teamSize = 3, seedValue = 10L)
        val board = Board.create("Main").addStep("Dev", AbilityName.DEVELOPER)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
        val org = Organization.create(name = "Org")
        val simulation = Simulation.create(name = "Sim", organization = org, scenario = scenario)

        val snapshot =
            DailySnapshot(
                simulationId = simulation.id,
                day = simulation.currentDay,
                metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 0.0),
                movements = emptyList(),
            )

        val appendedScenario = scenario.appendDecision(Decision.move("card-1")).appendSnapshot(snapshot)
        val advanced = simulation.copy(scenario = appendedScenario).advanceDay().withStatus(SimulationStatus.RUNNING)
        val result = SimulationResult(simulation = advanced, snapshot = snapshot)

        assertEquals(1, appendedScenario.decisions.size)
        assertEquals(1, appendedScenario.history.size)
        assertEquals(2, advanced.currentDay.value)
        assertEquals(SimulationStatus.RUNNING, advanced.status)
        assertEquals(policy.wipLimit, appendedScenario.rules.policySet.wipLimit)
        assertEquals(advanced.currentDay, result.simulation.currentDay)
    }

    @Test
    fun `given invalid value objects when constructing then validation fails`() {
        assertFailsWith<IllegalArgumentException> { SimulationDay(0) }
        assertFailsWith<IllegalArgumentException> { PolicySet(wipLimit = 0) }
        assertFailsWith<IllegalArgumentException> {
            FlowMetrics(throughput = -1, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
        }
    }

    @Test
    fun `given seeded random when generating daily capacities then deployer is unlimited and others stay bounded`() {
        val worker =
            Worker(
                name = "Dev",
                abilities =
                    setOf(
                        Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL),
                        Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL),
                    ),
            )

        val capacities = worker.generateDailyCapacities(Random(42), minPoints = 1, maxPoints = 2)

        assertEquals(Int.MAX_VALUE, capacities[AbilityName.DEPLOYER])
        assertTrue((capacities[AbilityName.DEVELOPER] ?: 0) in 1..2)
        assertEquals(0, capacities[AbilityName.TESTER])
    }

    private fun developerWorker(): Worker {
        val dev = Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)
        val deployer = Ability(name = AbilityName.DEPLOYER, seniority = Seniority.PL)
        return Worker(name = "Dev", abilities = setOf(dev, deployer))
    }

    private fun developmentStep(): Step =
        Step.create(
            boardId = "board-1",
            name = "Development",
            position = 0,
            requiredAbility = AbilityName.DEVELOPER,
        )

    private fun productManagerWorker(): Worker {
        val pm = Ability(name = AbilityName.PRODUCT_MANAGER, seniority = Seniority.SR)
        val deployer = Ability(name = AbilityName.DEPLOYER, seniority = Seniority.SR)
        return Worker(name = "PM", abilities = setOf(pm, deployer))
    }
}
