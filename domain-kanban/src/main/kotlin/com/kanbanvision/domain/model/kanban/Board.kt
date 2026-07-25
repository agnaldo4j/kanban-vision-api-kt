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

    fun toRef(): BoardId = id
}
