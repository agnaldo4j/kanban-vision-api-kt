package com.kanbanvision.httpapi

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kanbanvision.domain.model.AbilityName
import com.kanbanvision.domain.model.Board
import com.kanbanvision.domain.model.DailySnapshot
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.domain.model.Organization
import com.kanbanvision.domain.model.Scenario
import com.kanbanvision.domain.model.ScenarioRules
import com.kanbanvision.domain.model.Simulation
import com.kanbanvision.domain.model.SimulationDay
import com.kanbanvision.domain.model.SimulationStatus
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.Date

internal const val TEST_JWT_SECRET = "test-secret"
internal const val TEST_JWT_ISSUER = "kanban-vision-api"
internal const val TEST_JWT_AUDIENCE = "kanban-vision-clients"
internal const val TEST_JWT_REALM = "Kanban Vision API"

internal fun issueTestJwt(
    subject: String = "tester",
    organizationId: String = "org-1",
    ttlMs: Long = 60_000L,
): String =
    JWT
        .create()
        .withAudience(TEST_JWT_AUDIENCE)
        .withIssuer(TEST_JWT_ISSUER)
        .withSubject(subject)
        .withClaim("organizationId", organizationId)
        .withExpiresAt(Date(System.currentTimeMillis() + ttlMs))
        .sign(Algorithm.HMAC256(TEST_JWT_SECRET))

internal fun ApplicationTestBuilder.withJwt(token: String = issueTestJwt()): io.ktor.client.request.HttpRequestBuilder.() -> Unit =
    {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

internal fun fixtureSimulation(id: String = "sim-1"): Simulation {
    val board =
        Board(id = "board-1", name = "Main Board")
            .addStep(name = "Analysis", requiredAbility = AbilityName.PRODUCT_MANAGER)
            .addStep(name = "Development", requiredAbility = AbilityName.DEVELOPER)
    val scenario =
        Scenario(
            id = "scn-1",
            name = "Default Simulation Scenario",
            rules = ScenarioRules.create(wipLimit = 2, teamSize = 2, seedValue = 42L),
            board = board,
        )
    return Simulation(
        id = id,
        name = "Simulation",
        currentDay = SimulationDay(1),
        status = SimulationStatus.DRAFT,
        organization = Organization(id = "org-1", name = "Org"),
        scenario = scenario,
    )
}

internal fun fixtureSnapshot(
    simulationId: String = "sim-1",
    day: Int = 1,
): DailySnapshot =
    DailySnapshot(
        simulationId = simulationId,
        day = SimulationDay(day),
        metrics = FlowMetrics(throughput = 1, wipCount = 1, blockedCount = 0, avgAgingDays = 0.0),
        movements = emptyList(),
    )
