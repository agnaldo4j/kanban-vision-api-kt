package com.kanbanvision.usecases.card.queries

import com.kanbanvision.usecases.cqs.Query

data class GetCardQuery(
    val id: String,
) : Query {
    override fun validate() {
        require(id.isNotBlank()) { "Card id must not be blank" }
    }
}
