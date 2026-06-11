package com.kanbanvision.domain.model

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class DomainInvariantPropertyTest {
    @Test
    fun `refs reject any blank id`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { BoardRef(blank) }.isFailure &&
                    runCatching { StepRef(blank) }.isFailure &&
                    runCatching { SimulationRef(blank) }.isFailure &&
                    runCatching { ScenarioRef(blank) }.isFailure
            }
        }
    }

    @Test
    fun `refs accept any non-blank id`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { id ->
                runCatching { BoardRef(id) }.isSuccess &&
                    runCatching { StepRef(id) }.isSuccess &&
                    runCatching { SimulationRef(id) }.isSuccess &&
                    runCatching { ScenarioRef(id) }.isSuccess
            }
        }
    }

    @Test
    fun `Board create rejects any blank name`() {
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { Board.create(blank) }.isFailure
            }
        }
    }

    @Test
    fun `Board create succeeds for any non-blank name`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                runCatching { Board.create(name) }.isSuccess
            }
        }
    }

    @Test
    fun `Board addStep rejects any blank step name`() {
        val board = Board.create("Board")
        runBlocking {
            forAll(ARB_BLANK) { blank ->
                runCatching { board.addStep(blank, AbilityName.DEVELOPER) }.isFailure
            }
        }
    }

    @Test
    fun `Board addStep always rejects duplicate step names`() {
        runBlocking {
            forAll(ARB_NON_BLANK) { name ->
                val board = Board.create("Board").addStep(name, AbilityName.DEVELOPER)
                runCatching { board.addStep(name, AbilityName.DEVELOPER) }.isFailure
            }
        }
    }

    @Test
    fun `Board addStep succeeds when step names are distinct`() {
        runBlocking {
            forAll(ARB_NON_BLANK, ARB_NON_BLANK) { name1, name2 ->
                if (name1 == name2) return@forAll true
                val board = Board.create("Board").addStep(name1, AbilityName.DEVELOPER)
                runCatching { board.addStep(name2, AbilityName.DEVELOPER) }.isSuccess
            }
        }
    }

    @Test
    fun `ScenarioRules wipLimit must be positive — rejects zero and negative`() {
        runBlocking {
            forAll(Arb.int(BOUND_LOWER..0)) { invalid ->
                runCatching { ScenarioRules.create(wipLimit = invalid, teamSize = 1, seedValue = 0L) }.isFailure
            }
        }
    }

    @Test
    fun `ScenarioRules teamSize must be positive — rejects zero and negative`() {
        runBlocking {
            forAll(Arb.int(BOUND_LOWER..0)) { invalid ->
                runCatching { ScenarioRules.create(wipLimit = 1, teamSize = invalid, seedValue = 0L) }.isFailure
            }
        }
    }

    @Test
    fun `ScenarioRules create succeeds for any positive wipLimit and teamSize`() {
        runBlocking {
            forAll(Arb.int(1..BOUND_UPPER), Arb.int(1..BOUND_UPPER)) { wip, team ->
                runCatching { ScenarioRules.create(wipLimit = wip, teamSize = team, seedValue = 0L) }.isSuccess
            }
        }
    }

    @Test
    fun `FlowMetrics rejects any negative field value`() {
        runBlocking {
            forAll(Arb.int(BOUND_LOWER..-1)) { neg ->
                runCatching {
                    FlowMetrics(throughput = neg, wipCount = 0, blockedCount = 0, avgAgingDays = 0.0)
                }.isFailure &&
                    runCatching {
                        FlowMetrics(throughput = 0, wipCount = neg, blockedCount = 0, avgAgingDays = 0.0)
                    }.isFailure &&
                    runCatching {
                        FlowMetrics(throughput = 0, wipCount = 0, blockedCount = neg, avgAgingDays = 0.0)
                    }.isFailure
            }
        }
    }

    private companion object {
        const val NAME_MAX = 50
        const val BOUND_UPPER = 1000
        const val BOUND_LOWER = -1000
        val ARB_BLANK: Arb<String> = Arb.of("", " ", "   ", "\t", "\n")
        val ARB_NON_BLANK: Arb<String> =
            Arb.string(minSize = 1, maxSize = NAME_MAX).filter { it.isNotBlank() }
    }
}
