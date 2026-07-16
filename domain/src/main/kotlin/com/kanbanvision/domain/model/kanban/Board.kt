package com.kanbanvision.domain.model.kanban

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.BoardId
import com.kanbanvision.domain.model.Domain
import com.kanbanvision.domain.model.StepId
import java.util.UUID

data class Board(
    override val id: BoardId,
    val name: String,
    val steps: List<Step> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain<BoardId> {
    init {
        require(name.isNotBlank()) { "Board name must not be blank" }
    }

    companion object {
        fun create(name: String): Board {
            require(name.isNotBlank()) { "Board name must not be blank" }
            return Board(id = BoardId(UUID.randomUUID().toString()), name = name)
        }
    }

    fun addStep(
        name: String,
        requiredAbility: AbilityName,
    ): Board {
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(steps.none { it.name == name }) { "Step name '$name' already exists on this board" }
        val newStep = Step.create(board = toRef(), name = name, position = steps.size, requiredAbility = requiredAbility)
        return copy(steps = steps + newStep)
    }

    fun addCard(
        step: StepId,
        title: String,
        description: String = "",
    ): Board {
        val target = steps.firstOrNull { it.id == step } ?: error("Step ${step.value} not found in board ${id.value}")
        val newCard = Card.create(step = target.toRef(), title = title, description = description, position = target.cards.size)
        val updatedSteps =
            steps.map { currentStep ->
                if (currentStep.id == target.id) currentStep.copy(cards = currentStep.cards + newCard) else currentStep
            }
        return copy(steps = updatedSteps)
    }

    fun toRef(): BoardId = id
}
