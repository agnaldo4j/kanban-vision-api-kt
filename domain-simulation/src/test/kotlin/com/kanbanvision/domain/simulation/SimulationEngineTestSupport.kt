package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.kanban.Board
import com.kanbanvision.domain.model.organization.Organization
import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Scenario
import com.kanbanvision.domain.model.simulation.ScenarioRules
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationResult
import com.kanbanvision.domain.model.simulation.SimulationStatus
import java.time.Instant

/**
 * Test-only convenience overload: runs one day with a FIXED clock (`Instant.EPOCH`).
 *
 * Production always injects `now` at the edge (GAP-DK) so [SimulationEngine.runDay] is a pure,
 * referentially transparent function of `(simulation, decisions, seed, now)`. The behavioural engine
 * tests care about flow/metrics, not timestamps, so this overload pins the clock and keeps them focused.
 * Resolves for 3-argument call sites only; the real 4-argument member handles explicit-`now` tests.
 */
internal fun SimulationEngine.runDay(
    simulation: Simulation,
    decisions: List<Decision>,
    seed: Long,
): SimulationResult = runDay(simulation, decisions, seed, Instant.EPOCH)

/**
 * Fixture compartilhado do pacote: monta uma [Simulation] RUNNING em torno de um board já preparado.
 *
 * Vive aqui, e não em cada classe de teste, porque o setup era copiado verbatim em três arquivos — uma
 * mudança de assinatura em `Simulation.create`/`Scenario.create`/`ScenarioRules.create` daria três lugares
 * para achar, e a divergência entre as cópias não quebraria teste nenhum (review do PR #381).
 */
internal fun simulationFrom(
    board: Board,
    wipLimit: Int,
): Simulation {
    val rules = ScenarioRules.create(wipLimit = wipLimit, teamSize = 2, seedValue = 1L)
    val scenario = Scenario.create(name = "Scenario", rules = rules, board = board)
    return Simulation.create(
        name = "Simulation",
        organization = Organization.create("Org"),
        scenario = scenario,
        status = SimulationStatus.RUNNING,
    )
}
