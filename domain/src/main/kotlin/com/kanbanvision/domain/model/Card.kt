package com.kanbanvision.domain.model

import java.util.UUID

data class Card(
    val id: String,
    val columnId: String = "",
    val title: String,
    val description: String = "",
    val position: Int = 0,
    val serviceClass: ServiceClass = ServiceClass.STANDARD,
    val state: CardState = CardState.TODO,
    val agingDays: Int = 0,
    val audit: Audit = Audit(),
) {
    init {
        require(id.isNotBlank()) { "Card id must not be blank" }
        require(title.isNotBlank()) { "Card title must not be blank" }
        require(position >= 0) { "Card position must be non-negative" }
        require(agingDays >= 0) { "Card agingDays must be non-negative" }
    }

    val createdAt get() = audit.createdAt

    companion object {
        fun create(
            columnId: String,
            title: String,
            description: String = "",
            position: Int,
        ): Card {
            require(columnId.isNotBlank()) { "Card columnId must not be blank" }
            require(title.isNotBlank()) { "Card title must not be blank" }
            return Card(
                id = UUID.randomUUID().toString(),
                columnId = columnId,
                title = title,
                description = description,
                position = position,
            )
        }

        fun createSimulation(
            title: String,
            serviceClass: ServiceClass = ServiceClass.STANDARD,
        ): Card =
            Card(
                id = UUID.randomUUID().toString(),
                title = title,
                serviceClass = serviceClass,
                state = CardState.TODO,
                agingDays = 0,
            )
    }

    fun moveTo(
        targetColumnId: String,
        newPosition: Int,
    ): Card {
        require(targetColumnId.isNotBlank()) { "Card target columnId must not be blank" }
        require(newPosition >= 0) { "Card target position must be non-negative" }
        return copy(columnId = targetColumnId, position = newPosition)
    }

    fun advance(): Card =
        when (state) {
            CardState.TODO -> copy(state = CardState.IN_PROGRESS)
            CardState.IN_PROGRESS -> copy(state = CardState.DONE)
            CardState.BLOCKED -> copy(state = CardState.IN_PROGRESS)
            CardState.DONE -> this
        }

    fun block(): Card {
        require(state == CardState.IN_PROGRESS) { "Only IN_PROGRESS cards can be blocked" }
        return copy(state = CardState.BLOCKED)
    }

    fun incrementAge(): Card = copy(agingDays = agingDays + 1)
}
