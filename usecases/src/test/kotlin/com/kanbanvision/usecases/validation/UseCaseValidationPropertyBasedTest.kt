package com.kanbanvision.usecases.validation

import com.kanbanvision.usecases.card.commands.CreateCardCommand
import com.kanbanvision.usecases.card.commands.MoveCardCommand
import com.kanbanvision.usecases.scenario.commands.CreateScenarioCommand
import com.kanbanvision.usecases.scenario.commands.RunDayCommand
import com.kanbanvision.usecases.scenario.queries.GetFlowMetricsRangeQuery
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UseCaseValidationPropertyBasedTest {
    @Test
    fun `create card command accepts generated non-blank step and title`() {
        runTest {
            checkAll(
                Arb.int(1..1_000_000),
                Arb.int(1..1_000_000),
            ) { stepIdSeed, titleSeed ->
                val stepId = "step-$stepIdSeed"
                val title = "title-$titleSeed"
                val result = CreateCardCommand(stepId = stepId, title = title).validate()
                assertTrue(result.isRight())
            }
        }
    }

    @Test
    fun `move card command accepts generated non-negative positions`() {
        runTest {
            checkAll(
                Arb.int(1..1_000_000),
                Arb.int(1..1_000_000),
                Arb.int(0..10_000),
            ) { cardIdSeed, targetStepIdSeed, newPosition ->
                val cardId = "card-$cardIdSeed"
                val targetStepId = "step-$targetStepIdSeed"
                val result =
                    MoveCardCommand(
                        cardId = cardId,
                        targetStepId = targetStepId,
                        newPosition = newPosition,
                    ).validate()
                assertTrue(result.isRight())
            }
        }
    }

    @Test
    fun `move card command rejects any negative position`() {
        runTest {
            checkAll(Arb.int(Int.MIN_VALUE..-1)) { newPosition ->
                val result =
                    MoveCardCommand(
                        cardId = "card-1",
                        targetStepId = "step-1",
                        newPosition = newPosition,
                    ).validate()
                assertTrue(result.isLeft())
            }
        }
    }

    @Test
    fun `create scenario command accepts generated positive wip and team size`() {
        runTest {
            checkAll(
                Arb.int(1..1_000_000),
                Arb.int(1..10_000),
                Arb.int(1..10_000),
                Arb.long(),
            ) { organizationSeed, wipLimit, teamSize, seedValue ->
                val organizationId = "organization-$organizationSeed"
                val result = CreateScenarioCommand(organizationId, wipLimit, teamSize, seedValue).validate()
                assertTrue(result.isRight())
            }
        }
    }

    @Test
    fun `create scenario command rejects non-positive wip limit`() {
        runTest {
            checkAll(Arb.int(Int.MIN_VALUE..0)) { wipLimit ->
                val result =
                    CreateScenarioCommand(
                        organizationId = "organization-1",
                        wipLimit = wipLimit,
                        teamSize = 1,
                        seedValue = 0L,
                    ).validate()
                assertTrue(result.isLeft())
            }
        }
    }

    @Test
    fun `create scenario command rejects non-positive team size`() {
        runTest {
            checkAll(Arb.int(Int.MIN_VALUE..0)) { teamSize ->
                val result =
                    CreateScenarioCommand(
                        organizationId = "organization-1",
                        wipLimit = 1,
                        teamSize = teamSize,
                        seedValue = 0L,
                    ).validate()
                assertTrue(result.isLeft())
            }
        }
    }

    @Test
    fun `flow metrics range query accepts generated valid day ranges`() {
        runTest {
            checkAll(
                Arb.int(1..1_000_000),
                Arb.int(1..10_000),
                Arb.int(0..1_000),
            ) { scenarioSeed, fromDay, delta ->
                val scenarioId = "scenario-$scenarioSeed"
                val toDay = fromDay + delta
                val result =
                    GetFlowMetricsRangeQuery(
                        scenarioId = scenarioId,
                        fromDay = fromDay,
                        toDay = toDay,
                    ).validate()
                assertTrue(result.isRight())
            }
        }
    }

    @Test
    fun `flow metrics range query rejects generated ranges where toDay is before fromDay`() {
        runTest {
            checkAll(
                Arb.int(1..10_000),
                Arb.int(1..1_000),
            ) { fromDay, gap ->
                val toDay = fromDay - gap
                val result =
                    GetFlowMetricsRangeQuery(
                        scenarioId = "scenario-1",
                        fromDay = fromDay,
                        toDay = toDay,
                    ).validate()
                assertTrue(result.isLeft())
            }
        }
    }

    @Test
    fun `run day command accepts any non-blank scenario id`() {
        runTest {
            checkAll(Arb.int(1..1_000_000)) { scenarioSeed ->
                val scenarioId = "scenario-$scenarioSeed"
                val result = RunDayCommand(scenarioId = scenarioId, decisions = emptyList()).validate()
                assertTrue(result.isRight())
            }
        }
    }

    @Test
    fun `run day command rejects whitespace-only scenario ids`() {
        runTest {
            checkAll(Arb.int(1..64)) { size ->
                val result = RunDayCommand(scenarioId = " ".repeat(size), decisions = emptyList()).validate()
                assertTrue(result.isLeft())
            }
        }
    }
}
