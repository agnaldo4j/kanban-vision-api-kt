package com.kanbanvision.domain.model

import java.util.UUID

data class Scenario(
    override val id: String,
    val name: String,
    val rules: ScenarioRules,
    val board: Board,
    val decisions: List<Decision> = emptyList(),
    val history: List<DailySnapshot> = emptyList(),
    override val audit: Audit = Audit(),
) : Domain {
    init {
        require(id.isNotBlank()) { "Scenario id must not be blank" }
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
                id = UUID.randomUUID().toString(),
                name = name,
                rules = rules,
                board = board,
            )
        }
    }

    fun appendDecision(decision: Decision): Scenario = copy(decisions = decisions + decision)

    fun appendSnapshot(snapshot: DailySnapshot): Scenario = copy(history = history + snapshot)
}
