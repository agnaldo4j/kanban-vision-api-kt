package com.kanbanvision.domain.simulation

import com.kanbanvision.domain.model.simulation.Decision
import com.kanbanvision.domain.model.simulation.Simulation
import com.kanbanvision.domain.model.simulation.SimulationResult
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
