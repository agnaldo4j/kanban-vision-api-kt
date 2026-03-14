package com.kanbanvision.usecases.board.queries

import com.kanbanvision.usecases.cqs.Query

data class GetBoardQuery(
    val id: String,
) : Query {
    override fun validate() {
        require(id.isNotBlank()) { "Board id must not be blank" }
    }
}
