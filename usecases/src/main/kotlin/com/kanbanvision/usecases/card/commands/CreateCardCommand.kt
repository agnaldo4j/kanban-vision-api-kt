package com.kanbanvision.usecases.card.commands

import com.kanbanvision.usecases.cqs.Command

data class CreateCardCommand(
    val columnId: String,
    val title: String,
    val description: String = "",
) : Command {
    override fun validate() {
        require(columnId.isNotBlank()) { "Column id must not be blank" }
        require(title.isNotBlank()) { "Card title must not be blank" }
    }
}
