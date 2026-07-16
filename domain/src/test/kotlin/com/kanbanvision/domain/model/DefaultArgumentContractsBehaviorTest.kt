package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Scenario
import com.kanbanvision.domain.model.organization.ScenarioRules
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GAP-AW (ADR-0029): contratos dos default arguments — os testes do domínio
 * sempre constroem via factories/argumentos mínimos, deixando os branches de
 * id/audit explícitos e os no-arg de Audit sem exercício. Cada teste cobre os
 * DOIS lados do bridge de defaults e asserta o contrato do valor default.
 */
class DefaultArgumentContractsBehaviorTest {
    private companion object {
        const val CLOCK_TOLERANCE_SECONDS = 300L
    }

    private val fixedInstant = Instant.parse("2026-07-06T12:00:00Z")
    private val explicitAudit = Audit(createdAt = fixedInstant, updatedAt = fixedInstant)

    @Test
    fun `audit factory and touch without arguments stamp current time`() {
        // Janela tolerante (relógio de parede pode ajustar para trás) — o alvo
        // do teste é exercitar o bridge no-arg, não a monotonicidade do clock.
        val before = Instant.now().minusSeconds(CLOCK_TOLERANCE_SECONDS)
        val audit = Audit.now()
        val touched = audit.touch()
        val after = Instant.now().plusSeconds(CLOCK_TOLERANCE_SECONDS)
        assertTrue(audit.createdAt in before..after)
        assertTrue(touched.updatedAt in before..after)
        assertEquals(audit.createdAt, touched.createdAt)
    }

    @Test
    fun `kanban entities accept explicit id and audit or generate defaults`() {
        val ability = Ability(id = "ab-1", name = AbilityName.DEVELOPER, seniority = Seniority.PL, audit = explicitAudit)
        assertEquals("ab-1", ability.id)
        assertEquals(fixedInstant, ability.audit.createdAt)

        val worker = Worker(id = "w-1", name = "Dev", abilities = setOf(ability), audit = explicitAudit)
        assertEquals("w-1", worker.id)

        val step =
            Step(
                id = StepId("s-1"),
                board = BoardId("b-1"),
                name = "Dev",
                requiredAbility = AbilityName.DEVELOPER,
                audit = explicitAudit,
            )
        assertEquals(0, step.position)
        assertTrue(step.cards.isEmpty() && step.workers.isEmpty())

        val board = Board(id = BoardId("b-1"), name = "B", audit = explicitAudit)
        assertTrue(board.steps.isEmpty())

        val card = Card(id = CardId("c-1"), step = StepId("s-1"), title = "T", audit = explicitAudit)
        assertEquals(0, card.analysisEffort + card.developmentEffort + card.testEffort + card.deployEffort)
        assertNotNull(Card(step = StepId("s-1"), title = "T").id)
    }

    @Test
    fun `organization entities accept explicit id and audit or generate defaults`() {
        val squad = Squad(id = "sq-1", name = "Alpha", audit = explicitAudit)
        assertTrue(squad.workers.isEmpty())

        val tribe = Tribe(id = "t-1", name = "Core", squads = listOf(squad), audit = explicitAudit)
        assertEquals("t-1", tribe.id)

        val org = Organization(id = "o-1", name = "Org", tribes = listOf(tribe), audit = explicitAudit)
        assertEquals("o-1", org.id)

        val policySet = PolicySet(id = "p-1", wipLimit = 3, audit = explicitAudit)
        assertEquals("p-1", policySet.id)

        val rules =
            ScenarioRules(
                id = "r-1",
                policySet = policySet,
                wipLimit = 3,
                teamSize = 2,
                seedValue = 7L,
                audit = explicitAudit,
            )
        assertEquals("r-1", rules.id)

        val scenario =
            Scenario(
                id = ScenarioId("sc-1"),
                name = "S",
                rules = rules,
                board = Board(id = BoardId("b"), name = "B"),
                audit = explicitAudit,
            )
        assertEquals("sc-1", scenario.id.value)
    }

    @Test
    fun `simulation entities accept explicit id and audit or generate defaults`() {
        val metrics = FlowMetrics(id = "m-1", throughput = 1, wipCount = 2, blockedCount = 0, avgAgingDays = 1.5, audit = explicitAudit)
        assertEquals("m-1", metrics.id)

        val movement =
            Movement(
                id = "mv-1",
                type = MovementType.MOVED,
                cardId = CardId("c-1"),
                day = SimulationDay(1),
                reason = "moved",
                audit = explicitAudit,
            )
        assertEquals("mv-1", movement.id)

        val snapshot =
            DailySnapshot(
                id = "ds-1",
                simulation = SimulationId("sim-1"),
                scenario = ScenarioId("sc-1"),
                day = SimulationDay(1),
                metrics = metrics,
                movements = listOf(movement),
                audit = explicitAudit,
            )
        assertEquals("ds-1", snapshot.id)
    }

    @Test
    fun `simulation aggregate accepts explicit audit and defaults empty history`() {
        val rules = ScenarioRules.create(wipLimit = 3, teamSize = 2, seedValue = 7L)
        val simulation =
            Simulation(
                id = SimulationId("sim-1"),
                name = "Sim",
                currentDay = SimulationDay(1),
                status = SimulationStatus.DRAFT,
                organization = Organization.create(name = "Org"),
                scenario = Scenario.create(name = "S", rules = rules),
                audit = explicitAudit,
            )
        assertTrue(simulation.decisions.isEmpty() && simulation.history.isEmpty())
        assertEquals(fixedInstant, simulation.audit.createdAt)
    }
}
