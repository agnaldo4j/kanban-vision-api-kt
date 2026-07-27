package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.StepId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GAP-DP: `Board.redistributeCards` — a distribuição de cards nos steps é invariante do agregado
 * (antes era extensão privada do `SimulationEngine`). Substitui os cards de cada step pelos da lista
 * que apontam para ele, ordenados por `position`.
 */
class BoardCardRedistributionBehaviorTest {
    private fun boardWithTwoSteps(): Board =
        Board
            .create(name = "Flow")
            .withStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
            .withStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)

    @Test
    fun `given cards pointing at other steps when redistributing then each card lands on the step it points to`() {
        val board = boardWithTwoSteps()
        val (analysis, development) = board.steps
        val moved = Card.create(step = development.id, title = "Was in analysis", position = 0)
        val stayed = Card.create(step = analysis.id, title = "Still in analysis", position = 0)

        val updated = board.redistributeCards(listOf(moved, stayed))

        assertEquals(
            listOf(stayed.id),
            updated.steps
                .single { it.id == analysis.id }
                .cards
                .map { it.id },
        )
        assertEquals(
            listOf(moved.id),
            updated.steps
                .single { it.id == development.id }
                .cards
                .map { it.id },
        )
    }

    @Test
    fun `given cards out of order when redistributing then step cards are ordered by position`() {
        val board = boardWithTwoSteps()
        val stepId = board.steps.first().id
        val third = Card.create(step = stepId, title = "Third", position = 2)
        val first = Card.create(step = stepId, title = "First", position = 0)
        val second = Card.create(step = stepId, title = "Second", position = 1)

        val updated = board.redistributeCards(listOf(third, first, second))

        assertEquals(
            listOf(first.id, second.id, third.id),
            updated.steps
                .first()
                .cards
                .map { it.id },
        )
    }

    @Test
    fun `given step whose cards are absent from the list when redistributing then that step is left empty`() {
        val board = boardWithTwoSteps()
        val (analysis, development) = board.steps
        val boardWithCards =
            board
                .withCard(step = analysis.id, title = "Analysis card")
                .withCard(step = development.id, title = "Development card")
        val survivor =
            boardWithCards.steps
                .single { it.id == development.id }
                .cards
                .single()

        val updated = boardWithCards.redistributeCards(listOf(survivor))

        assertEquals(
            emptyList(),
            updated.steps
                .single { it.id == analysis.id }
                .cards
                .map { it.id },
        )
        assertEquals(
            listOf(survivor.id),
            updated.steps
                .single { it.id == development.id }
                .cards
                .map { it.id },
        )
    }

    @Test
    fun `given card pointing at a step outside this board when redistributing then the card is dropped`() {
        val board = boardWithTwoSteps()
        val stepId = board.steps.first().id
        val kept = Card.create(step = stepId, title = "Belongs here", position = 0)
        val orphan = Card.create(step = StepId("step-from-another-board"), title = "Orphan", position = 0)

        val updated = board.redistributeCards(listOf(kept, orphan))

        assertEquals(listOf(kept.id), updated.steps.flatMap { step -> step.cards.map { it.id } })
    }

    @Test
    fun `given cards spread over steps when asking for all cards then they come flattened in step order`() {
        val board = boardWithTwoSteps()
        val (analysis, development) = board.steps
        val boardWithCards =
            board
                .withCard(step = analysis.id, title = "Analysis first")
                .withCard(step = development.id, title = "Development first")
                .withCard(step = analysis.id, title = "Analysis second")

        val titles = boardWithCards.allCards().map { it.title.value }

        assertEquals(listOf("Analysis first", "Analysis second", "Development first"), titles)
    }

    @Test
    fun `given board without cards when asking for all cards then the list is empty`() {
        assertEquals(emptyList(), boardWithTwoSteps().allCards())
    }

    @Test
    fun `given board when redistributing its own cards then the board is unchanged`() {
        val board = boardWithTwoSteps()
        val (analysis, development) = board.steps
        val boardWithCards =
            board
                .withCard(step = analysis.id, title = "Analysis card")
                .withCard(step = development.id, title = "Development card")

        // Par leitura/escrita do mesmo invariante. A ida-e-volta é identidade para um board NORMALIZADO —
        // cards de cada step já em ordem de `position`, que é como `addCard` os produz. Não é lei geral: o
        // decode preserva a ordem do array JSON sem reordenar, então um blob fora de ordem volta reordenado.
        assertEquals(boardWithCards, boardWithCards.redistributeCards(boardWithCards.allCards()))
    }

    @Test
    fun `given card whose step field disagrees with the step holding it when reading all cards then nesting wins`() {
        val board = boardWithTwoSteps()
        val (analysis, development) = board.steps
        val misfiled = Card.create(step = development.id, title = "Misfiled", position = 0)
        // Blob inconsistente: o card está guardado em `analysis`, mas seu campo `step` aponta para `development`.
        val corrupted = board.copy(steps = listOf(analysis.copy(cards = listOf(misfiled)), development))

        val roundTripped = corrupted.redistributeCards(corrupted.allCards())

        // Sem o carimbo do `allCards`, o card seria descartado aqui — e o save seguinte o apagaria do banco.
        assertEquals(listOf(misfiled.id), roundTripped.allCards().map { it.id })
        assertEquals(
            listOf(misfiled.id),
            roundTripped.steps
                .single { it.id == analysis.id }
                .cards
                .map { it.id },
        )
    }

    @Test
    fun `given empty card list when redistributing then every step is emptied and step identity is preserved`() {
        val board = boardWithTwoSteps()
        val stepId = board.steps.first().id
        val boardWithCard = board.withCard(step = stepId, title = "To be dropped")

        val updated = boardWithCard.redistributeCards(emptyList())

        assertEquals(emptyList(), updated.steps.flatMap { it.cards })
        assertEquals(board.steps.map { it.id }, updated.steps.map { it.id })
        assertEquals(board.steps.map { it.name.value }, updated.steps.map { it.name.value })
        assertEquals(board.steps.map { it.position }, updated.steps.map { it.position })
    }
}
