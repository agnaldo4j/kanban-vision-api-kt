package com.kanbanvision.domain.model.kanban

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.organization.PolicySet
import com.kanbanvision.domain.model.organization.Squad
import com.kanbanvision.domain.model.organization.Tribe
import com.kanbanvision.domain.model.withCard
import com.kanbanvision.domain.model.withStep
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Fecha as lacunas de cobertura kanban/organization que antes viviam nos testes mistos mantidos no
 * `:domain`: leitura de todos os getters (audit/seniority/effort/board/name), os guards de id/name em
 * branco por entidade e o bridge de argumento-default de `Board.addCard` (sem description).
 */
class KanbanDataClassContractsTest {
    private val audit = Audit.now(Instant.parse("2026-03-20T00:00:00Z"))

    @Test
    fun `given kanban topology entities when reading properties then getters expose the constructor values`() {
        val ability = Ability(id = "a-1", name = AbilityName.DEVELOPER, seniority = Seniority.SR, audit = audit)
        assertEquals(Seniority.SR, ability.seniority)
        assertEquals(audit, ability.audit)

        val board = Board(id = BoardId("b-1"), name = "Board", audit = audit)
        assertEquals("Board", board.name)
        assertEquals(audit, board.audit)

        val step = Step(id = StepId("s-1"), board = BoardId("b-1"), name = "Dev", requiredAbility = AbilityName.DEVELOPER, audit = audit)
        assertEquals(BoardId("b-1"), step.board)
        assertEquals(audit, step.audit)

        val worker = Worker(name = "Dev", abilities = setOf(ability), audit = audit)
        assertEquals(audit, worker.audit)
    }

    @Test
    fun `given a card when reading properties then getters expose the constructor values`() {
        val card =
            Card(
                id = CardId("c-1"),
                step = StepId("s-1"),
                title = "Card",
                description = "desc",
                analysisEffort = 2,
                developmentEffort = 3,
                testEffort = 4,
                deployEffort = 5,
            )
        assertEquals(CardId("c-1"), card.id)
        assertEquals("desc", card.description)
        assertEquals(2, card.analysisEffort)
        assertEquals(3, card.developmentEffort)
        assertEquals(4, card.testEffort)
        assertEquals(5, card.deployEffort)
        assertEquals(4, card.remainingTestEffort)
    }

    @Test
    fun `given organization entities when reading audit then getters expose the constructor value`() {
        assertEquals(audit, Organization(id = "o-1", name = "Org", audit = audit).audit)
        assertEquals(audit, Squad(name = "Squad", audit = audit).audit)
        assertEquals(audit, Tribe(name = "Tribe", audit = audit).audit)
        assertEquals(audit, PolicySet(wipLimit = 3, audit = audit).audit)
    }

    @Test
    fun `given blank identifiers or names when constructing entities then creation is rejected`() {
        assertFailsWith<IllegalArgumentException> { Ability(id = "", name = AbilityName.DEVELOPER, seniority = Seniority.PL) }
        assertFailsWith<IllegalArgumentException> { Board(id = BoardId("b-1"), name = "") }
        assertFailsWith<IllegalArgumentException> { Organization(id = "", name = "Org") }
        assertFailsWith<IllegalArgumentException> { Organization(id = "o-1", name = "") }
        assertFailsWith<IllegalArgumentException> { Squad(id = "", name = "Squad") }
        assertFailsWith<IllegalArgumentException> { Tribe(id = "", name = "Tribe") }
    }

    @Test
    fun `given board with a step when adding a card with default description then the card lands on the step`() {
        val board = Board.create(name = "Board").withStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
        val stepId = board.steps.first().id

        val updated = board.withCard(step = stepId, title = "Card")

        assertEquals(
            1,
            updated.steps
                .first()
                .cards.size,
        )
        assertEquals(
            "",
            updated.steps
                .first()
                .cards
                .first()
                .description,
        )
    }
}
