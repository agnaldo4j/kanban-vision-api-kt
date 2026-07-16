package com.kanbanvision.domain.model

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.SimulationDay
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class IdentifierInvariantPropertyTest {
    @Test
    fun `Movement rejects any blank id`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching {
                    Movement(id = blank, type = MovementType.MOVED, cardId = CardId("c-1"), day = SimulationDay(1), reason = "r")
                }.isFailure
            }
        }
    }

    @Test
    fun `Movement rejects any blank cardId`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching {
                    Movement(type = MovementType.MOVED, cardId = CardId(blank), day = SimulationDay(1), reason = "r")
                }.isFailure
            }
        }
    }

    @Test
    fun `Movement accepts any non-blank id and cardId`() {
        runBlocking {
            forAll(ARB_NON_BLANK, ARB_NON_BLANK) { id, cardId ->
                runCatching {
                    Movement(id = id, type = MovementType.MOVED, cardId = CardId(cardId), day = SimulationDay(1), reason = "r")
                }.isSuccess
            }
        }
    }

    @Test
    fun `DailySnapshot rejects any blank id`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching {
                    DailySnapshot(
                        id = blank,
                        simulation = SIM_REF,
                        scenario = SCENARIO_REF,
                        day = SimulationDay(1),
                        metrics = METRICS,
                        movements = emptyList(),
                    )
                }.isFailure
            }
        }
    }

    @Test
    fun `DailySnapshot accepts any non-blank id`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                runCatching {
                    DailySnapshot(
                        id = id,
                        simulation = SIM_REF,
                        scenario = SCENARIO_REF,
                        day = SimulationDay(1),
                        metrics = METRICS,
                        movements = emptyList(),
                    )
                }.isSuccess
            }
        }
    }

    @Test
    fun `Ability rejects any blank id`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching {
                    Ability(id = blank, name = AbilityName.DEVELOPER, seniority = Seniority.PL)
                }.isFailure
            }
        }
    }

    @Test
    fun `Ability accepts any non-blank id`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                runCatching {
                    Ability(id = id, name = AbilityName.DEVELOPER, seniority = Seniority.PL)
                }.isSuccess
            }
        }
    }

    private companion object {
        const val ID_MAX_LENGTH = 50
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = ID_MAX_LENGTH).filter { it.isNotBlank() }
        val SIM_REF = SimulationId("sim-1")
        val SCENARIO_REF = ScenarioId("sc-1")
        val METRICS = FlowMetrics(throughput = 0, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
    }
}
