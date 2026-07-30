package com.kanbanvision.domain.model.kanban

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.common.model.NonBlankName
import java.util.UUID

data class Board(
    override val id: BoardId,
    val name: NonBlankName,
    val steps: List<Step> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<BoardId> {
    companion object {
        fun create(name: String): Board = Board(id = BoardId(UUID.randomUUID().toString()), name = NonBlankName(name))
    }

    // ADR-0044: falha de REGRA de domínio → Either (raise KanbanError). A precondição de construção
    // (nome não-vazio) segue `require` no `Step.create`/`Card.create` — fail-fast em bug do chamador.
    fun addStep(
        name: String,
        requiredAbility: AbilityName,
    ): Either<KanbanError, Board> =
        either {
            ensure(steps.none { it.name.value == name }) { KanbanError.DuplicateStepName(name) }
            val newStep = Step.create(board = toRef(), name = name, position = steps.size, requiredAbility = requiredAbility)
            copy(steps = steps + newStep)
        }

    fun addCard(
        step: StepId,
        title: String,
        description: String = "",
    ): Either<KanbanError, Board> =
        either {
            val target = steps.firstOrNull { it.id == step } ?: raise(KanbanError.StepNotFound(step.value))
            val newCard = Card.create(step = target.toRef(), title = title, description = description, position = target.cards.size)
            copy(
                steps =
                    steps.map { currentStep ->
                        if (currentStep.id == target.id) currentStep.copy(cards = currentStep.cards + newCard) else currentStep
                    },
            )
        }

    // O `copy(step = step.id)` não é redundante: `Card.step` é serializado à parte do aninhamento e o decode
    // não reconcilia os dois. Divergindo, `redistributeCards` descartaria o card — e como o repositório
    // re-serializa o agregado inteiro a cada save, o descarte é DELEÇÃO permanente (`migrations.md`).
    fun allCards(): List<Card> = steps.flatMap { step -> step.cards.map { card -> card.copy(step = step.id) } }

    // Substitui os cards de cada step, não faz merge — e card cujo `step` não pertence a este board é
    // DESCARTADO. Ver o carimbo em `allCards()` para por que isso é perda de dado, não filtro.
    fun redistributeCards(cards: List<Card>): Board {
        val cardsByStep = cards.groupBy { it.step }
        val updatedSteps =
            steps.map { step ->
                step.copy(cards = cardsByStep[step.id].orEmpty().sortedBy { it.position })
            }
        return copy(steps = updatedSteps)
    }

    // `sortedBy` é estável: empate em `position` mantém a ordem de inserção, e o `runDay` depende disso
    // para ser determinístico.
    fun stepsInExecutionOrder(): List<Step> = steps.sortedBy { it.position }

    fun firstStep(): Step? = stepsInExecutionOrder().firstOrNull()

    // Não delega a `allCards().size`: aquele carimba cada card, alocando N cópias só para contar. A
    // igualdade entre os dois é lei em `BoardCardRedistributionPropertyTest`.
    fun itemCount(): Int = steps.sumOf { it.cards.size }

    fun toRef(): BoardId = id
}
