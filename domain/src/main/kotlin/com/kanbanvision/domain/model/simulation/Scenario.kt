package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.model.Audit
import com.kanbanvision.domain.model.Domain
import com.kanbanvision.domain.model.ScenarioId
import com.kanbanvision.domain.model.kanban.Board
import java.util.UUID

data class Scenario(
    override val id: ScenarioId,
    val name: String,
    val rules: ScenarioRules,
    val board: Board,
    override val audit: Audit = Audit(),
) : Domain<ScenarioId> {
    init {
        require(name.isNotBlank()) { "Scenario name must not be blank" }
    }

    companion object {
        fun create(
            name: String,
            rules: ScenarioRules,
            board: Board = Board.create(name = "Main Board"),
        ): Scenario {
            require(name.isNotBlank()) { "Scenario name must not be blank" }
            return Scenario(
                id = ScenarioId(UUID.randomUUID().toString()),
                name = name,
                rules = rules,
                board = board,
            )
        }
    }

    fun toRef(): ScenarioId = id
}
