package com.kanbanvision.usecases.board.commands

import com.kanbanvision.usecases.cqs.Command

data class CreateBoardCommand(
    val name: String,
) : Command {
    override fun validate() {
        require(name.isNotBlank()) { "Board name must not be blank" }
    }
}
