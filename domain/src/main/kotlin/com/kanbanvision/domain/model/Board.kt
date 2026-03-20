package com.kanbanvision.domain.model

import java.util.UUID

data class Board(
    override val id: String,
    val name: String,
    val steps: List<Step> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Board id must not be blank" }
        require(name.isNotBlank()) { "Board name must not be blank" }
    }

    companion object {
        fun create(name: String): Board {
            require(name.isNotBlank()) { "Board name must not be blank" }
            return Board(id = UUID.randomUUID().toString(), name = name)
        }
    }

    fun addStep(
        name: String,
        requiredAbility: AbilityName,
    ): Board {
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(steps.none { it.name == name }) { "Step name '$name' already exists on this board" }
        val newStep = Step.create(boardId = id, name = name, position = steps.size, requiredAbility = requiredAbility)
        return copy(steps = steps + newStep)
    }

    fun addCard(
        stepId: String,
        title: String,
        description: String = "",
    ): Board {
        val target = steps.firstOrNull { it.id == stepId } ?: error("Step $stepId not found in board $id")
        val newCard = Card.create(stepId = target.id, title = title, description = description, position = target.cards.size)
        val updatedSteps =
            steps.map { step ->
                if (step.id == stepId) step.copy(cards = step.cards + newCard) else step
            }
        return copy(steps = updatedSteps)
    }
}
