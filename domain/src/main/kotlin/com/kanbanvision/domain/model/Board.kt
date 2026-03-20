package com.kanbanvision.domain.model

import java.util.UUID

data class Board(
    val id: String,
    val name: String,
    val steps: List<Step> = emptyList(),
    val audit: Audit = Audit(),
) {
    init {
        require(id.isNotBlank()) { "Board id must not be blank" }
        require(name.isNotBlank()) { "Board name must not be blank" }
    }

    val createdAt get() = audit.createdAt

    companion object {
        fun create(name: String): Board {
            require(name.isNotBlank()) { "Board name must not be blank" }
            return Board(id = UUID.randomUUID().toString(), name = name)
        }
    }

    fun addStep(
        name: String,
        requiredAbility: AbilityName,
    ): Step {
        require(name.isNotBlank()) { "Step name must not be blank" }
        require(steps.none { it.name == name }) { "Step name '$name' already exists on this board" }
        return Step.create(boardId = id, name = name, position = steps.size, requiredAbility = requiredAbility)
    }

    fun addCard(
        step: Step,
        title: String,
        description: String = "",
    ): Card {
        require(step.boardId == id) { "Step ${step.id} does not belong to board $id" }
        return Card.create(stepId = step.id, title = title, description = description, position = step.cards.size)
    }
}
