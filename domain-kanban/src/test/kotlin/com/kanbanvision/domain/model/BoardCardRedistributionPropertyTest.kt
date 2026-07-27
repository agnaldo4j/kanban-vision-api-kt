package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * GAP-DP: as LEIS do par leitura/escrita `allCards` ⇄ `redistributeCards`, para qualquer board — inclusive
 * os inconsistentes que o decode pode produzir (`Card.step` é serializado à parte do aninhamento em
 * `StepSurrogate.cards`, e o decode não reconcilia os dois).
 *
 * Property test porque as leis são universais: os casos literais em [BoardCardRedistributionBehaviorTest]
 * fixam exemplos, mas só o gerador cobre posição duplicada, step vazio e campo `step` divergente juntos.
 */
class BoardCardRedistributionPropertyTest {
    @Test
    fun `redistributing a board's own cards never loses a card`() {
        runBlocking {
            forAll(ARB_BOARD) { board ->
                val normalized = board.redistributeCards(board.allCards())
                normalized.allCards().map { it.id }.toSet() == board.allCards().map { it.id }.toSet()
            }
        }
    }

    @Test
    fun `redistributing orders every step by position`() {
        runBlocking {
            forAll(ARB_BOARD) { board ->
                board.redistributeCards(board.allCards()).steps.all { step ->
                    step.cards.map { it.position } == step.cards.map { it.position }.sorted()
                }
            }
        }
    }

    @Test
    fun `redistributing is idempotent - normalizing a normalized board changes nothing`() {
        runBlocking {
            forAll(ARB_BOARD) { board ->
                val once = board.redistributeCards(board.allCards())
                once.redistributeCards(once.allCards()) == once
            }
        }
    }

    @Test
    fun `redistributing never adds, removes or reorders steps`() {
        runBlocking {
            forAll(ARB_BOARD) { board ->
                board.redistributeCards(board.allCards()).steps.map { it.id } == board.steps.map { it.id }
            }
        }
    }

    private companion object {
        const val MAX_STEPS = 3
        const val MAX_CARDS_PER_STEP = 4
        const val MAX_POSITION = 3

        /**
         * Board com steps e cards arbitrários. Os cards são anexados direto ao `Step` (não via `addCard`)
         * de propósito: só assim dá para gerar `position` repetida e um `Card.step` que DIVERGE do step que
         * guarda o card — que é exatamente o estado que o decode pode materializar.
         *
         * O `step` de cada card sai de [ARB_TARGET_STEP], que inclui um id FORA do board. Sem esse caso o
         * gerador só produz relocação (o card muda de step, mas sobrevive) e a lei de não-perda passaria
         * mesmo com o bug — medido: sem o carimbo do `allCards`, as 4 propriedades passavam.
         */
        val ARB_BOARD: Arb<Board> =
            arbitrary {
                val stepCount = Arb.int(1..MAX_STEPS).bind()
                val skeleton =
                    (0 until stepCount).fold(Board.create(name = "Flow")) { board, index ->
                        board.withStep(name = "Step $index", requiredAbility = AbilityName.entries[index % AbilityName.entries.size])
                    }
                val stepIds = skeleton.steps.map { step -> step.id }
                skeleton.copy(steps = skeleton.steps.map { step -> step.withArbitraryCards(stepIds).bind() })
            }

        /** Um step do próprio board, ou um id órfão — o card cujo step sumiu num release anterior. */
        private fun arbTargetStep(stepIds: List<StepId>): Arb<StepId> =
            arbitrary {
                val index = Arb.int(0..stepIds.size).bind()
                if (index == stepIds.size) StepId("step-outside-this-board") else stepIds[index]
            }

        private fun Step.withArbitraryCards(stepIds: List<StepId>): Arb<Step> =
            arbitrary {
                val cardCount = Arb.int(0..MAX_CARDS_PER_STEP).bind()
                val cards =
                    (0 until cardCount).map { index ->
                        Card.create(
                            step = arbTargetStep(stepIds).bind(),
                            title = "Card $index",
                            position = Arb.int(0..MAX_POSITION).bind(),
                        )
                    }
                copy(cards = cards)
            }
    }
}
