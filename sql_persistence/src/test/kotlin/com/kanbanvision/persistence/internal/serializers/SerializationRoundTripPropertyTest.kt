package com.kanbanvision.persistence.internal.serializers

import com.kanbanvision.domain.model.kanban.Ability
import com.kanbanvision.domain.model.kanban.AbilityName
import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.kanban.CardId
import com.kanbanvision.domain.model.kanban.Seniority
import com.kanbanvision.domain.model.kanban.Worker
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.DailySnapshot
import com.kanbanvision.domain.model.simulation.FlowMetrics
import com.kanbanvision.domain.model.simulation.Movement
import com.kanbanvision.domain.model.simulation.MovementType
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioId
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationDay
import com.kanbanvision.domain.model.simulation.SimulationId
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * GAP-AU: round-trip de serialização dos agregados persistidos como JSONB —
 * para QUALQUER agregado válido gerado pelas factories do domínio,
 * decode(encode(x)) == x. Pega perda de campo, default silencioso e problema
 * de encoding que testes example-based não pegam.
 */
class SerializationRoundTripPropertyTest {
    // Nomes com acentos, aspas, escapes e unicode — onde JSON costuma quebrar.
    private val arbName =
        Arb.of("Simulação \"β\"", "Órg & Cia", "a\\b/c", "linha\nquebrada", "日本語", "x", "Wörk 100%")

    private val arbAggregate: Arb<Simulation> =
        arbitrary { rs ->
            val stepCount = Arb.int(1..3).bind()
            val cardsPerStep = Arb.int(0..3).bind()
            var board = Board.create(arbName.bind())
            val abilities = AbilityName.entries
            repeat(stepCount) { i ->
                board = board.addStep(name = "step-$i-${arbName.bind()}", requiredAbility = abilities[i % abilities.size])
            }
            board.steps.forEach { step ->
                repeat(cardsPerStep) { c ->
                    board = board.addCard(step = step.toRef(), title = "card-$c-${arbName.bind()}", description = arbName.bind())
                }
            }
            // List(n) em vez de Arb.list: o Kotest pode repetir a MESMA amostra na
            // lista (mesmo id), e assignWorker rejeita worker duplicado no step.
            // Worker versátil (todas as abilities): steps têm requiredAbility
            // arbitrário e assignWorker exige compatibilidade.
            val workerCount = Arb.int(1..3).bind()
            val workers =
                List(workerCount) {
                    val seniority = Arb.enum<Seniority>().bind()
                    Worker(
                        name = arbName.bind(),
                        abilities = AbilityName.entries.map { ability -> Ability(name = ability, seniority = seniority) }.toSet(),
                    )
                }
            board = board.copy(steps = board.steps.map { s -> workers.fold(s) { acc, w -> acc.assignWorker(w) } })

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
                movements =
                    Arb
                        .list(
                            arbitrary {
                                Movement(
                                    type = Arb.enum<MovementType>().bind(),
                                    cardId = CardId("card-${Arb.int(1..999).bind()}"),
                                    day = SimulationDay(Arb.int(1..365).bind()),
                                    reason = Arb.string(0..20).bind(),
                                )
                            },
                            0..4,
                        ).bind(),
            )
        }

    @Test
    fun `simulation aggregate survives encode decode round trip`() {
        runBlocking {
            forAll(iterations = 300, arbAggregate) { simulation ->
                SimulationSerializer.decode(SimulationSerializer.encode(simulation)) == simulation
            }
        }
    }

    @Test
    fun `daily snapshot survives encode decode round trip`() {
        runBlocking {
            forAll(iterations = 300, arbSnapshot) { snapshot ->
                DailySnapshotSerializer.decode(DailySnapshotSerializer.encode(snapshot)) == snapshot
            }
        }
    }
}
