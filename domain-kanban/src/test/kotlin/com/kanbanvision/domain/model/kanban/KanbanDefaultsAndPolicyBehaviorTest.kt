package com.kanbanvision.domain.model.kanban

import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.common.model.NonBlankTitle
import com.kanbanvision.domain.model.blockOrThrow
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
        val board = Board(id = BoardId("b-1"), name = NonBlankName("Board"))
        val step = Step(board = BoardId("b-1"), name = NonBlankName("Dev"), requiredAbility = AbilityName.DEVELOPER)
        val card = Card(step = StepId("s-1"), title = NonBlankTitle("Card"))
        val worker = Worker(name = NonBlankName("Dev"), abilities = setOf(ability))

        assertTrue(board.steps.isEmpty())
        assertEquals(0, step.position)
        assertEquals(CardState.TODO, card.state)
        assertEquals(ServiceClass.STANDARD, card.serviceClass)
        assertTrue(worker.abilities.contains(ability))
    }

    @Test
    fun `given a service class name when parsing tolerantly then known names resolve and anything else becomes standard`() {
        assertEquals(ServiceClass.EXPEDITE, ServiceClass.fromNameOrDefault("EXPEDITE"))
        assertEquals(ServiceClass.INTANGIBLE, ServiceClass.fromNameOrDefault("INTANGIBLE"))
        assertEquals(ServiceClass.STANDARD, ServiceClass.fromNameOrDefault("NOT_A_CLASS"))
        assertEquals(ServiceClass.STANDARD, ServiceClass.fromNameOrDefault(""))
        assertEquals(ServiceClass.STANDARD, ServiceClass.fromNameOrDefault(null))
        assertEquals(ServiceClass.STANDARD, ServiceClass.fromNameOrDefault("expedite"), "o parse é sensível a caixa")
    }

    @Test
    fun `given a service class when creating a card through the factory then the factory honours it`() {
        val expedite = Card.create(step = StepId("s-1"), title = "Card", position = 0, serviceClass = ServiceClass.EXPEDITE)

        assertEquals(ServiceClass.EXPEDITE, expedite.serviceClass)
    }

    @Test
    fun `given no service class when creating a card through the factory then it defaults to standard`() {
        val card = Card.create(step = StepId("s-1"), title = "Card", position = 0)

        assertEquals(ServiceClass.STANDARD, card.serviceClass)
    }

    @Test
    fun `given minimal constructors when using defaults then organization entities are created`() {
        val org = Organization(id = "o-1", name = NonBlankName("Org"))
        val squad = Squad(name = NonBlankName("Squad"))
        val tribe = Tribe(name = NonBlankName("Tribe"))

        assertTrue(org.tribes.isEmpty())
        assertTrue(squad.workers.isEmpty())
        assertTrue(tribe.squads.isEmpty())
    }

    @Test
    fun `given in-progress card when blocked then state becomes blocked`() {
        val card = Card(step = StepId("s-1"), title = NonBlankTitle("Card")).advance()

        val blocked = card.blockOrThrow()

        assertEquals(CardState.BLOCKED, blocked.state)
    }

    @Test
    fun `given non in-progress card when blocked then it is rejected`() {
        val card = Card(step = StepId("s-1"), title = NonBlankTitle("Card"))

        assertIs<KanbanError.CardNotInProgress>(card.block().leftOrNull())
    }

    @Test
    fun `given blocked card when unblocked then state becomes in progress`() {
        val blocked = Card(step = StepId("s-1"), title = NonBlankTitle("Card")).advance().blockOrThrow()

        assertEquals(CardState.IN_PROGRESS, blocked.unblock().getOrNull()?.state)
    }

    @Test
    fun `given non blocked card when unblocked then it is rejected`() {
        val todo = Card(step = StepId("s-1"), title = NonBlankTitle("Card"))

        val error = assertIs<KanbanError.CardNotBlocked>(todo.unblock().leftOrNull())
        assertEquals(todo.id.value, error.cardId)
        assertIs<KanbanError.CardNotBlocked>(todo.advance().unblock().leftOrNull())
        assertIs<KanbanError.CardNotBlocked>(
            todo
                .advance()
                .advance()
                .unblock()
                .leftOrNull(),
        )
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

    @Test
    fun `ServiceClass carries its own scheduling policy — rank order and shuffle tiers`() {
        // OOD (enum-carrega-comportamento): a política de agendamento mora no enum, não num when do engine.
        val byRank = ServiceClass.entries.sortedBy { it.schedulingRank }.map { it.name }
        assertEquals(listOf("EXPEDITE", "FIXED_DATE", "STANDARD", "INTANGIBLE"), byRank)
        // ranks distintos definem a ordem total do agendamento (contrato de que o engine depende).
        assertEquals(
            ServiceClass.entries.size,
            ServiceClass.entries
                .map { it.schedulingRank }
                .toSet()
                .size,
        )

        assertTrue(ServiceClass.STANDARD.shuffleWithinTier)
        assertTrue(ServiceClass.INTANGIBLE.shuffleWithinTier)
        assertEquals(false, ServiceClass.EXPEDITE.shuffleWithinTier)
        assertEquals(false, ServiceClass.FIXED_DATE.shuffleWithinTier)
    }
}
