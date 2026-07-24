package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationResult
import com.kanbanvision.domain.model.simulation.SimulationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Clock purity of the engine (GAP-DK): `now` was lifted out of `runDay` and is injected at the edge, so
 * the timestamp path is now a **pure function of the injected `now`** — no hidden `Instant.now()` inside
 * the engine. These tests pin that: `now` lands in the audit timestamp, is deterministic across runs, and
 * is the *only* thing it affects (flow stays seed-deterministic).
 *
 * Note: `runDay` is not yet byte-identical across runs because `Movement`/`DailySnapshot`/`FlowMetrics`
 * still mint `UUID.randomUUID()` identities on construction — a separate identity concern (value-class IDs,
 * GAP-DJ/DL), out of scope here. GAP-DK closes the *clock* impurity only.
 */
class SimulationEnginePurityBehaviorTest {
    @Test
    fun `given same seed and now when running day twice then injected now lands in audit deterministically`() {
        val simulation = simulationWithInProgressDevCard()
        val now = Instant.parse("2026-03-04T05:06:07Z")

        val first = executedDevCard(SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 7L, now = now))
        val second = executedDevCard(SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 7L, now = now))

        assertEquals(now, first.audit.updatedAt)
        assertEquals(now, second.audit.updatedAt)
    }

    @Test
    fun `given different now with same seed when running day then only the audit timestamp changes`() {
        val simulation = simulationWithInProgressDevCard()
        val early = Instant.parse("2026-01-01T00:00:00Z")
        val late = Instant.parse("2026-12-31T23:59:59Z")

        val runEarly = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 7L, now = early)
        val runLate = SimulationEngine.runDay(simulation, decisions = emptyList(), seed = 7L, now = late)

        // The executed card's audit timestamp is exactly the injected `now` (no hidden clock).
        val cardEarly = executedDevCard(runEarly)
        val cardLate = executedDevCard(runLate)
        assertEquals(early, cardEarly.audit.updatedAt)
        assertEquals(late, cardLate.audit.updatedAt)

        // `now` must NOT influence the seed-deterministic flow: same effort consumed, same state, same metrics.
        assertEquals(cardEarly.remainingDevelopmentEffort, cardLate.remainingDevelopmentEffort)
        assertEquals(cardEarly.state, cardLate.state)
        assertEquals(runEarly.snapshot.metrics.throughput, runLate.snapshot.metrics.throughput)
        assertEquals(runEarly.snapshot.metrics.wipCount, runLate.snapshot.metrics.wipCount)
        assertEquals(runEarly.snapshot.movements.map { it.type }, runLate.snapshot.movements.map { it.type })
    }

    private fun executedDevCard(result: SimulationResult): Card =
        result.simulation.scenario.board.steps
            .flatMap { it.cards }
            .first { it.id == CardId("dev-card") }

    private fun simulationWithInProgressDevCard(): Simulation {
        val rules = ScenarioRules.create(wipLimit = 2, teamSize = 4, seedValue = 99L)
        val scenario = Scenario.create(name = "Scenario", rules = rules, board = executableDevBoard())
        return Simulation.create(
            name = "Simulation",
            organization = Organization.create(name = "Org"),
            scenario = scenario,
            status = SimulationStatus.RUNNING,
        )
    }

    private fun executableDevBoard(): Board {
        val base = Board.create(name = "Main").withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
        val devStep = base.steps.first { it.requiredAbility == AbilityName.DEVELOPER }
        val developer = Worker(name = "Dev", abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
        return base.copy(
            steps =
                base.steps.map { step ->
                    if (step.id == devStep.id) step.withWorker(developer).copy(cards = listOf(inProgressDevCard(step.id))) else step
                },
        )
    }

    private fun inProgressDevCard(stepId: StepId): Card =
        Card(
            id = CardId("dev-card"),
            step = stepId,
            title = "Build",
            state = CardState.IN_PROGRESS,
            developmentEffort = 3,
            remainingDevelopmentEffort = 3,
        )
}
