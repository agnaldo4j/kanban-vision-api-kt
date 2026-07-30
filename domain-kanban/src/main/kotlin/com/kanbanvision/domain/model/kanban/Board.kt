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

    fun allCards(): List<Card> = steps.flatMap { it.cardsStampedWithOwningStep() }

    private fun Step.cardsStampedWithOwningStep(): List<Card> = cards.map { it.copy(step = id) }

    fun redistributeCards(cards: List<Card>): Board {
        val cardsByStep = cards.groupBy { it.step }
        val updatedSteps =
            steps.map { step ->
                step.copy(cards = cardsByStep[step.id].orEmpty().sortedBy { it.position })
            }
        return copy(steps = updatedSteps)
    }

    fun stepsInExecutionOrder(): List<Step> = steps.sortedBy { it.position }

    fun firstStep(): Step? = stepsInExecutionOrder().firstOrNull()

    // Não delega a `allCards().size`: aquele aloca uma cópia por card só para contar.
    fun itemCount(): Int = steps.sumOf { it.cards.size }

    fun toRef(): BoardId = id
}
