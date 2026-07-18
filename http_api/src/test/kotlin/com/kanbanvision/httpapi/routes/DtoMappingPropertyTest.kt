package com.kanbanvision.httpapi.routes

import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * GAP-AU: propriedades dos mapeamentos DTO ↔ domínio — para QUALQUER agregado
 * válido, os campos expostos pela API preservam os valores do domínio; e os
 * Request DTOs sobrevivem a round-trip kotlinx.serialization.
 */
class DtoMappingPropertyTest {
    // Mesma configuração dos demais testes de rota (ex.: SimulationDtosAndErrorsTest).
    private val json = Json { ignoreUnknownKeys = true }
    private val arbName = Arb.of("Simulação \"β\"", "Órg & Cia", "a\\b/c", "日本語", "x", "Wörk 100%")
    private val decisionTypes = listOf("MOVE_ITEM", "BLOCK_ITEM", "UNBLOCK_ITEM", "ADD_ITEM")

    private val arbSimulation: Arb<Simulation> =
        arbitrary {
            val cardCount = Arb.int(0..4).bind()
            var board = Board.create(arbName.bind()).addStep(name = "Dev", requiredAbility = AbilityName.DEVELOPER)
            repeat(cardCount) { c ->
                board = board.addCard(step = board.steps.first().toRef(), title = "card-$c")
            }
            val rules =
                ScenarioRules.create(
                    wipLimit = Arb.int(1..10).bind(),
                    teamSize = Arb.int(1..8).bind(),
                    seedValue = Arb.long(0L..Long.MAX_VALUE / 2).bind(),
                )
            Simulation.create(
                name = arbName.bind(),
                organization = Organization.create(name = arbName.bind()),
                scenario = Scenario.create(name = arbName.bind(), rules = rules, board = board),
            )
        }

    private val arbSnapshot: Arb<DailySnapshot> =
        arbitrary {
            DailySnapshot(
                simulation = SimulationId("sim-${Arb.int(1..999).bind()}"),
                scenario = ScenarioId("sc-${Arb.int(1..999).bind()}"),
                day = SimulationDay(Arb.int(1..365).bind()),
                metrics =
                    FlowMetrics(
                        throughput = Arb.int(0..50).bind(),
                        wipCount = Arb.int(0..50).bind(),
                        blockedCount = Arb.int(0..50).bind(),
                        // numericDouble: no Kotest 6 os edge cases de Arb.double incluem
                        // NaN mesmo com range — e NaN >= 0 é false no require do FlowMetrics.
                        avgAgingDays = Arb.numericDouble(0.0, 90.0).bind(),
                    ),
                movements = emptyList(),
            )
        }

    @Test
    fun `simulation response preserves domain values for any aggregate`() {
        runBlocking {
            forAll(iterations = 300, arbSimulation) { sim ->
                val r = sim.toSimulationResponse()
                r.simulationId == sim.id.value &&
                    r.organizationId == sim.organization.id &&
                    r.wipLimit == sim.scenario.rules.wipLimit &&
                    r.teamSize == sim.scenario.rules.teamSize &&
                    r.seedValue == sim.scenario.rules.seedValue &&
                    r.state.currentDay == sim.currentDay.value &&
                    r.state.wipLimit == sim.scenario.rules.policySet.wipLimit &&
                    r.state.teamSize == sim.scenario.rules.teamSize &&
                    r.state.itemCount ==
                    sim.scenario.board.steps
                        .sumOf { it.cards.size }
            }
        }
    }

    @Test
    fun `summary response preserves identity name status and day`() {
        runBlocking {
            forAll(iterations = 300, arbSimulation) { sim ->
                val s = sim.toSummaryResponse()
                s.id == sim.id.value && s.name == sim.name && s.status == sim.status.name && s.currentDay == sim.currentDay.value
            }
        }
    }

    @Test
    fun `days response preserves size order and metrics`() {
        runBlocking {
            forAll(iterations = 200, Arb.list(arbSnapshot, 0..6)) { snapshots ->
                val r = snapshots.toDaysResponse("sim-1")
                r.days.size == snapshots.size &&
                    r.days.zip(snapshots).all { (dto, snap) ->
                        dto.day == snap.day.value &&
                            dto.throughput == snap.metrics.throughput &&
                            dto.wipCount == snap.metrics.wipCount &&
                            dto.blockedCount == snap.metrics.blockedCount &&
                            dto.avgAgingDays == snap.metrics.avgAgingDays
                    }
            }
        }
    }

    @Test
    fun `decision request with known type and cardId or title maps to Right`() {
        runBlocking {
            forAll(iterations = 300, Arb.of(decisionTypes), Arb.string(1..12).map { "id-$it" }) { type, value ->
                val payloadKey = if (type == "ADD_ITEM") "title" else "cardId"
                DecisionRequest(type = type, payload = mapOf(payloadKey to value)).toDomain().isRight()
            }
        }
    }

    @Test
    fun `decision request with unknown type or missing field maps to Left`() {
        runBlocking {
            forAll(iterations = 300, Arb.string(1..12).filter { it.uppercase() !in decisionTypes }) { junk ->
                DecisionRequest(type = junk, payload = mapOf("cardId" to "c1")).toDomain().isLeft()
            }
            forAll(iterations = 100, Arb.of(decisionTypes)) { type ->
                DecisionRequest(type = type, payload = emptyMap()).toDomain().isLeft()
            }
        }
    }

    @Test
    fun `create simulation request survives kotlinx json round trip`() {
        runBlocking {
            forAll(
                iterations = 300,
                arbitrary {
                    CreateSimulationRequest(
                        wipLimit = Arb.int(1..100).bind(),
                        teamSize = Arb.int(1..50).bind(),
                        seedValue = Arb.long().bind(),
                    )
                },
            ) { req ->
                json.decodeFromString<CreateSimulationRequest>(json.encodeToString(CreateSimulationRequest.serializer(), req)) == req
            }
        }
    }

    @Test
    fun `run day request survives kotlinx json round trip`() {
        runBlocking {
            forAll(
                iterations = 200,
                arbitrary {
                    RunDayRequest(
                        decisions =
                            Arb
                                .list(
                                    arbitrary {
                                        DecisionRequest(
                                            type = Arb.of(decisionTypes).bind(),
                                            payload = mapOf("cardId" to "c-${Arb.int(1..99).bind()}"),
                                        )
                                    },
                                    0..3,
                                ).bind(),
                    )
                },
            ) { req ->
                json.decodeFromString<RunDayRequest>(json.encodeToString(RunDayRequest.serializer(), req)) == req
            }
        }
    }
}
