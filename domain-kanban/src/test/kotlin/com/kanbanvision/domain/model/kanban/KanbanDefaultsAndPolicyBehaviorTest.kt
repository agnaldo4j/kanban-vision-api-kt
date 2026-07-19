package com.kanbanvision.domain.model.kanban

import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Cobre os bridges de argumento-default (`= Audit()`, `= emptyList()`, ids default) e os caminhos
 * kanban/organization cuja cobertura vivia só em testes mistos que permaneceram no `:domain`
 * (PolicySet, Card.block). Construções mínimas exercitam os defaults; os requires cobrem os guards.
 */
class KanbanDefaultsAndPolicyBehaviorTest {
    private val ability = Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)

    @Test
    fun `given minimal constructors when using defaults then kanban entities are created`() {
        val board = Board(id = BoardId("b-1"), name = "Board")
        val step = Step(board = BoardId("b-1"), name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val card = Card(step = StepId("s-1"), title = "Card")
        val worker = Worker(name = "Dev", abilities = setOf(ability))

        assertTrue(board.steps.isEmpty())
        assertEquals(0, step.position)
        assertEquals(CardState.TODO, card.state)
        assertEquals(ServiceClass.STANDARD, card.serviceClass)
        assertTrue(worker.abilities.contains(ability))
    }

    @Test
    fun `given minimal constructors when using defaults then organization entities are created`() {
        val org = Organization(id = "o-1", name = "Org")
        val squad = Squad(name = "Squad")
        val tribe = Tribe(name = "Tribe")

        assertTrue(org.tribes.isEmpty())
        assertTrue(squad.workers.isEmpty())
        assertTrue(tribe.squads.isEmpty())
    }

    @Test
    fun `given in-progress card when blocked then state becomes blocked`() {
        val card = Card(step = StepId("s-1"), title = "Card").advance()

        val blocked = card.block()

        assertEquals(CardState.BLOCKED, blocked.state)
    }

    @Test
    fun `given non in-progress card when blocked then it is rejected`() {
        val card = Card(step = StepId("s-1"), title = "Card")

        assertFailsWith<IllegalArgumentException> { card.block() }
    }

    @Test
    fun `given policy set when wip limit is valid then defaults are applied`() {
        val policy = PolicySet(wipLimit = 3)

        assertEquals(3, policy.wipLimit)
        assertTrue(policy.id.isNotBlank())
    }

    @Test
    fun `given policy set when wip limit is non positive then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { PolicySet(wipLimit = 0) }
    }

    @Test
    fun `given policy set when id is blank then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { PolicySet(id = "", wipLimit = 1) }
    }
}
