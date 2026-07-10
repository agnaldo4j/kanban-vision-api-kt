package com.kanbanvision.httpapi.routes

import com.kanbanvision.httpapi.TEST_JWT_AUDIENCE
import com.kanbanvision.httpapi.TEST_JWT_ISSUER
import com.kanbanvision.httpapi.TEST_JWT_REALM
import com.kanbanvision.httpapi.TEST_JWT_SECRET
import com.kanbanvision.httpapi.plugins.RequestIdPlugin
import com.kanbanvision.httpapi.plugins.configureAuthentication
import com.kanbanvision.httpapi.plugins.configureSerialization
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fail-closed de tenancy: cada handler protegido, exercitado SEM principal (rota fora de
 * `authenticate`), deve responder 401 antes de tocar o caso de uso — cobrindo o ramo nulo de
 * `callerOrganizationId()` em todo endpoint (security.md §A10, GAP-BJ).
 */
class SimulationRoutesTenancyGuardTest {
    private fun assertFailsClosed(
        response: HttpResponse,
        body: String,
    ) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(body.contains("Missing organization context"))
    }

    private fun Application.mount(build: Route.() -> Unit) {
        configureSerialization()
        // RequestIdPlugin presente: o 401 fail-closed carrega o requestId do MDC (ramo não-"unknown").
        install(RequestIdPlugin)
        // Plugin de auth instalado, rota FORA de authenticate: o handler roda sem principal.
        configureAuthentication(TEST_JWT_SECRET, TEST_JWT_ISSUER, TEST_JWT_AUDIENCE, TEST_JWT_REALM)
        routing(build)
    }

    private suspend fun ApplicationTestBuilder.callGet(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = client.get(path, block)

    @Test
    fun `given no principal when creating simulation then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { mount { post("/t/create") { call.handleCreateSimulation(mocks.createSimulationUseCase) } } }
            val response = client.post("/t/create")
            assertFailsClosed(response, response.bodyAsText())
        }

    @Test
    fun `given no principal when getting simulation then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { mount { get("/t/{simulationId}") { call.handleGetSimulation(mocks.getSimulationUseCase) } } }
            val response = callGet("/t/sim-1")
            assertFailsClosed(response, response.bodyAsText())
        }

    @Test
    fun `given no principal when running day then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { mount { post("/t/{simulationId}/run") { call.handleRunDay(mocks.runDayUseCase) } } }
            val response = client.post("/t/sim-1/run")
            assertFailsClosed(response, response.bodyAsText())
        }

    @Test
    fun `given no principal when getting daily snapshot then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application {
                mount { get("/t/{simulationId}/{day}") { call.handleGetDailySnapshot(mocks.getDailySnapshotUseCase) } }
            }
            val response = callGet("/t/sim-1/1")
            assertFailsClosed(response, response.bodyAsText())
        }

    @Test
    fun `given missing day path param when getting daily snapshot then handler returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            // Rota sem {day}: exercita o requiredPathParam("day") faltante → 400 antes do guard.
            application {
                mount { get("/t/{simulationId}") { call.handleGetDailySnapshot(mocks.getDailySnapshotUseCase) } }
            }
            val response = callGet("/t/sim-1")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Missing day"))
        }

    @Test
    fun `given no principal when fetching days then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { mount { get("/t/{simulationId}/days") { call.handleGetSimulationDays(mocks.getSimulationDaysUseCase) } } }
            val response = callGet("/t/sim-1/days")
            assertFailsClosed(response, response.bodyAsText())
        }

    @Test
    fun `given no principal when fetching cfd then handler fails closed`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { mount { get("/t/{simulationId}/cfd") { call.handleGetSimulationCfd(mocks.getSimulationCfdUseCase) } } }
            val response = callGet("/t/sim-1/cfd")
            assertFailsClosed(response, response.bodyAsText())
        }
}
