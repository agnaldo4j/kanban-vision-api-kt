package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.BoardId
import com.kanbanvision.domain.model.kanban.Card
import com.kanbanvision.domain.model.kanban.CardState
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Step
import com.kanbanvision.domain.model.kanban.StepId
import com.kanbanvision.domain.model.kanban.Worker
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fixa a suposição de **totalidade** que o `SimulationEngine` explora (ADR-0044): quando o pré-guard do
 * engine é satisfeito, as operações `Either`-returning **nunca** falham (nunca `Left`) — é o que torna
 * seguro o `.onRight { }` que atualiza o estado sem tratar um `Left` impossível. Aqui a suposição é
 * **verificada por teste**, não só documentada em comentário (review do PR #350).
 */
class TypedOperationTotalityBehaviorTest {
    @Test
    fun `given worker eligible for the step when executing card then result is Right`() {
        val dev = Worker(name = "Dev", abilities = setOf(Ability(name = AbilityName.DEVELOPER, seniority = Seniority.PL)))
        val step =
            Step
                .create(board = BoardId("b-1"), name = "Dev", position = 0, requiredAbility = AbilityName.DEVELOPER)
                .withWorker(dev)
        val card = Card(step = step.id, title = "Task", state = CardState.IN_PROGRESS, developmentEffort = 1)

        assertTrue(step.executeCard(dev, card, mapOf(AbilityName.DEVELOPER to 1), Instant.EPOCH).isRight())
    }

    @Test
    fun `given in progress card when blocking then result is Right`() {
        val inProgress = Card(step = StepId("s-1"), title = "Card").advance()

        assertTrue(inProgress.block().isRight())
    }
}
