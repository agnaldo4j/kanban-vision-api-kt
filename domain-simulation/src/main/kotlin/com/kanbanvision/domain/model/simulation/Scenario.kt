package com.kanbanvision.domain.model.simulation

import com.kanbanvision.domain.common.model.Audit
import com.kanbanvision.domain.common.model.Domain
import com.kanbanvision.domain.common.model.NonBlankName
import com.kanbanvision.domain.model.kanban.Board
import java.util.UUID

data class Scenario(
    override val id: ScenarioId,
    val name: NonBlankName,
    val rules: ScenarioRules,
    val board: Board,
    override val audit: Audit = Audit(),
) : Domain<ScenarioId> {
    companion object {
        fun create(
            name: String,
            rules: ScenarioRules,
            board: Board = Board.create(name = "Main Board"),
        ): Scenario =
            Scenario(
                id = ScenarioId(UUID.randomUUID().toString()),
                name = NonBlankName(name),
                rules = rules,
                board = board,
            )
    }

    // Delegação de UM salto (GAP-DQ): o cenário não sabe contar cards, o board sabe. Um salto por nível é
    // o que impede que consertar a cadeia do DTO signifique escrever outra cadeia aqui dentro.
    fun itemCount(): Int = board.itemCount()

    fun toRef(): ScenarioId = id
}
