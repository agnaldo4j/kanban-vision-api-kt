package com.kanbanvision.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DomainIdValidationCoverageTest {
    @Test
    fun `board card and step reject blank ids`() {
        assertFailsWith<IllegalArgumentException> {
            Board(id = "", name = "Board")
        }
        assertFailsWith<IllegalArgumentException> {
            Card(id = "", columnId = "column-1", title = "Card", position = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "", boardId = "board-1", name = "Step", requiredAbility = AbilityName.DEVELOPER)
        }
        assertFailsWith<IllegalArgumentException> {
            Step(id = "step-1", boardId = "", name = "Step", requiredAbility = AbilityName.DEVELOPER)
        }
    }

    @Test
    fun `scenario organization and card reject blank ids`() {
        assertFailsWith<IllegalArgumentException> {
            Scenario(id = "", organizationId = "organization-1", config = ScenarioConfig(1, 1, 0L))
        }
        assertFailsWith<IllegalArgumentException> {
            Scenario(id = "scenario-1", organizationId = "", config = ScenarioConfig(1, 1, 0L))
        }
        assertFailsWith<IllegalArgumentException> {
            Organization(id = "", name = "Organization")
        }
        assertFailsWith<IllegalArgumentException> {
            Card(id = "", title = "Item", serviceClass = ServiceClass.STANDARD, state = CardState.TODO, agingDays = 0)
        }
    }

    @Test
    fun `movement and daily snapshot reject blank references`() {
        assertFailsWith<IllegalArgumentException> {
            Movement(type = MovementType.MOVED, cardId = "", day = SimulationDay(1), reason = "reason")
        }
        assertFailsWith<IllegalArgumentException> {
            DailySnapshot(
                scenarioId = "",
                day = SimulationDay(1),
                metrics = FlowMetrics(1, 1, 0, 0.0),
                movements = emptyList(),
            )
        }
    }
}
