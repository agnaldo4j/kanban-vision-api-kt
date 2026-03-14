package com.kanbanvision.usecases.card.commands

import com.kanbanvision.usecases.cqs.Command

data class MoveCardCommand(
    val cardId: String,
    val targetColumnId: String,
    val newPosition: Int,
) : Command {
    override fun validate() {
        require(cardId.isNotBlank()) { "Card id must not be blank" }
        require(targetColumnId.isNotBlank()) { "Target column id must not be blank" }
        require(newPosition >= 0) { "Position must be non-negative" }
    }
}
