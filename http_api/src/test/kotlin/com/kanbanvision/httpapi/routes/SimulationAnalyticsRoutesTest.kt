package com.kanbanvision.httpapi.routes

import arrow.core.left
import arrow.core.right
import com.kanbanvision.domain.errors.DomainError
import com.kanbanvision.domain.model.FlowMetrics
import com.kanbanvision.httpapi.fixtureSimulation
import com.kanbanvision.httpapi.fixtureSnapshot
import com.kanbanvision.httpapi.plugins.configureSerialization
import com.kanbanvision.httpapi.withJwt
import com.kanbanvision.usecases.Page
import com.kanbanvision.usecases.simulation.CfdDataPoint
import com.kanbanvision.usecases.simulation.CfdResult
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationAnalyticsRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `given valid organization id when listing simulations then api returns paginated list`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val simulations = listOf(fixtureSimulation("s1"), fixtureSimulation("s2"))
            coEvery { mocks.listSimulationsUseCase.execute(any()) } returns
                Page(data = simulations, page = 1, size = 20, total = 2L).right()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?organizationId=org-1") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val payload = json.decodeFromString<SimulationListResponse>(body)
            assertEquals(2, payload.data.size)
            assertEquals(1, payload.page)
            assertEquals(20, payload.size)
            assertEquals(2L, payload.total)
        }

    @Test
    fun `given missing organization id when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("organizationId"))
        }

    @Test
    fun `given validation error when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.listSimulationsUseCase.execute(any()) } returns
                DomainError.ValidationError("Page must be at least 1").left()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?organizationId=org-1&page=0") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `given valid simulation id when fetching days then api returns time series`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val snapshots =
                listOf(
                    fixtureSnapshot(simulationId = "sim-1", day = 1)
                        .copy(metrics = FlowMetrics(throughput = 3, wipCount = 5, blockedCount = 1, avgAgingDays = 1.0)),
                    fixtureSnapshot(simulationId = "sim-1", day = 2)
                        .copy(metrics = FlowMetrics(throughput = 2, wipCount = 4, blockedCount = 0, avgAgingDays = 1.5)),
                )
            coEvery { mocks.getSimulationDaysUseCase.execute(any()) } returns snapshots.right()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations/sim-1/days") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<SimulationDaysResponse>(response.bodyAsText())
            assertEquals("sim-1", payload.simulationId)
            assertEquals(2, payload.days.size)
            assertEquals(3, payload.days[0].throughput)
            assertEquals(4, payload.days[1].wipCount)
        }

    @Test
    fun `given persistence error when fetching days then api returns internal server error`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.getSimulationDaysUseCase.execute(any()) } returns
                DomainError.PersistenceError("db error").left()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations/sim-1/days") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `given valid simulation id when fetching cfd then api returns cumulative flow data`() =
        testApplication {
            val mocks = SimulationApiMocks()
            val cfdResult =
                CfdResult(
                    simulationId = "sim-1",
                    series =
                        listOf(
                            CfdDataPoint(day = 1, throughputCumulative = 3, wipCount = 5, blockedCount = 1),
                            CfdDataPoint(day = 2, throughputCumulative = 5, wipCount = 4, blockedCount = 0),
                        ),
                )
            coEvery { mocks.getSimulationCfdUseCase.execute(any()) } returns cfdResult.right()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations/sim-1/cfd") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.decodeFromString<SimulationCfdResponse>(response.bodyAsText())
            assertEquals("sim-1", payload.simulationId)
            assertEquals(2, payload.series.size)
            assertEquals(3, payload.series[0].throughputCumulative)
            assertEquals(5, payload.series[1].throughputCumulative)
        }

    @Test
    fun `given persistence error when fetching cfd then api returns internal server error`() =
        testApplication {
            val mocks = SimulationApiMocks()
            coEvery { mocks.getSimulationCfdUseCase.execute(any()) } returns
                DomainError.PersistenceError("db error").left()

            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations/sim-1/cfd") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `given non-integer page parameter when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?organizationId=org-1&page=abc") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid page"))
        }

    @Test
    fun `given non-integer size parameter when listing simulations then api returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response =
                client.get("/api/v1/simulations?organizationId=org-1&size=xyz") {
                    withJwt().invoke(this)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Invalid size"))
        }

    @Test
    fun `given unauthenticated request when listing simulations then api returns unauthorized`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application { configureSimulationApi(mocks) }

            val response = client.get("/api/v1/simulations?organizationId=org-1")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `given missing simulation id path param when fetching days then handler returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application {
                configureSerialization()
                routing {
                    get("/test/days-no-id") { call.handleGetSimulationDays(mocks.getSimulationDaysUseCase) }
                }
            }

            val response = client.get("/test/days-no-id")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Missing simulation id"))
        }

    @Test
    fun `given missing simulation id path param when fetching cfd then handler returns bad request`() =
        testApplication {
            val mocks = SimulationApiMocks()
            application {
                configureSerialization()
                routing {
                    get("/test/cfd-no-id") { call.handleGetSimulationCfd(mocks.getSimulationCfdUseCase) }
                }
            }

            val response = client.get("/test/cfd-no-id")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Missing simulation id"))
        }
}
